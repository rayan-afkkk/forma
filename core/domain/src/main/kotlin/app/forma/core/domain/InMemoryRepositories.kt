package app.forma.core.domain

import app.forma.core.model.ActiveSessionState
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExerciseExclusion
import app.forma.core.model.ExerciseId
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.ProgramEnrollment
import app.forma.core.model.ScheduleMove
import app.forma.core.model.SessionFeedback
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.UserState
import app.forma.core.model.WorkoutPlan
import app.forma.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * In-memory repositories. Used by unit tests and by the JVM verification harness; the Android app
 * uses Room and DataStore implementations with the same contracts.
 */

class InMemoryUserStateRepository(initial: UserState = UserState()) : UserStateRepository {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<UserState> = flow
    override suspend fun current(): UserState = flow.value
    override suspend fun update(transform: (UserState) -> UserState): UserState {
        flow.update(transform)
        return flow.value
    }
    override suspend fun clear() {
        flow.value = UserState()
    }
}

class InMemoryEquipmentRepository : EquipmentRepository {
    private val flow = MutableStateFlow<List<EquipmentProfile>>(emptyList())
    override val profiles: Flow<List<EquipmentProfile>> = flow
    override suspend fun all() = flow.value
    override suspend fun upsert(profile: EquipmentProfile) = flow.update { list ->
        if (list.any { it.id == profile.id }) list.map { if (it.id == profile.id) profile else it } else list + profile
    }
    override suspend fun delete(id: String) = flow.update { list -> list.filterNot { it.id == id } }
    override suspend fun clear() {
        flow.value = emptyList()
    }
}

class InMemoryExclusionRepository : ExclusionRepository {
    private val flow = MutableStateFlow<List<ExerciseExclusion>>(emptyList())
    override val exclusions: Flow<List<ExerciseExclusion>> = flow
    override suspend fun all() = flow.value
    override suspend fun add(exerciseId: ExerciseId, createdAt: Long) = flow.update { list ->
        if (list.any { it.exerciseId == exerciseId }) list else list + ExerciseExclusion(exerciseId, createdAt)
    }
    override suspend fun remove(exerciseId: ExerciseId) = flow.update { list -> list.filterNot { it.exerciseId == exerciseId } }
    override suspend fun clear() {
        flow.value = emptyList()
    }
}

class InMemoryFavoritesRepository : FavoritesRepository {
    private val flow = MutableStateFlow<Set<String>>(emptySet())
    override val favorites: Flow<Set<String>> = flow
    override suspend fun all() = flow.value
    override suspend fun set(templateId: String, favorite: Boolean) = flow.update { if (favorite) it + templateId else it - templateId }
    override suspend fun clear() {
        flow.value = emptySet()
    }
}

class InMemoryProgramRepository : ProgramRepository {
    private val enrollmentFlow = MutableStateFlow<ProgramEnrollment?>(null)
    private val movesFlow = MutableStateFlow<List<ScheduleMove>>(emptyList())
    private val adjustmentsFlow = MutableStateFlow<Map<String, PlanAdjustments>>(emptyMap())

    override val enrollment: Flow<ProgramEnrollment?> = enrollmentFlow
    override suspend fun currentEnrollment() = enrollmentFlow.value
    override suspend fun setEnrollment(enrollment: ProgramEnrollment?) {
        enrollmentFlow.value = enrollment
    }

    override val moves: Flow<List<ScheduleMove>> = movesFlow
    override suspend fun allMoves() = movesFlow.value
    override suspend fun addMove(move: ScheduleMove) = movesFlow.update { list -> list.filterNot { it.fromDate == move.fromDate } + move }

    private fun key(date: String, planKey: String) = "$date|$planKey"
    override fun adjustments(date: String, planKey: String): Flow<PlanAdjustments> =
        adjustmentsFlow.map { it[key(date, planKey)] ?: PlanAdjustments() }
    override suspend fun currentAdjustments(date: String, planKey: String) = adjustmentsFlow.value[key(date, planKey)] ?: PlanAdjustments()
    override suspend fun setAdjustments(date: String, planKey: String, adjustments: PlanAdjustments) =
        adjustmentsFlow.update { it + (key(date, planKey) to adjustments) }

    override suspend fun clear() {
        enrollmentFlow.value = null
        movesFlow.value = emptyList()
        adjustmentsFlow.value = emptyMap()
    }
}

class InMemorySessionRepository : SessionRepository {
    private val flow = MutableStateFlow<List<WorkoutSession>>(emptyList())
    private val active = MutableStateFlow<ActiveSessionState?>(null)

    override val sessions: Flow<List<WorkoutSession>> = flow.map { list -> list.sortedByDescending { it.startedAt } }
    override suspend fun all() = flow.value.sortedByDescending { it.startedAt }
    override fun observe(id: String): Flow<WorkoutSession?> = flow.map { list -> list.firstOrNull { it.id == id } }
    override suspend fun get(id: String) = flow.value.firstOrNull { it.id == id }

    private fun modify(id: String, transform: (WorkoutSession) -> WorkoutSession) =
        flow.update { list -> list.map { if (it.id == id) transform(it) else it } }

    override suspend fun insert(session: WorkoutSession) = flow.update { list ->
        require(list.none { it.id == session.id }) { "Session ${session.id} already exists" }
        list + session
    }
    override suspend fun updatePlan(id: String, plan: WorkoutPlan) = modify(id) { it.copy(plan = plan) }
    override suspend fun upsertSet(set: SetLog) = modify(set.sessionId) { session ->
        session.copy(sets = session.sets.filterNot { it.id == set.id } + set)
    }
    override suspend fun deleteSet(sessionId: String, itemKey: String, setNumber: Int) = modify(sessionId) { session ->
        session.copy(sets = session.sets.filterNot { it.itemKey == itemKey && it.setNumber == setNumber })
    }
    override suspend fun setSkipped(id: String, itemKeys: Set<String>) = modify(id) { it.copy(skippedItemKeys = itemKeys) }

    override suspend fun finish(id: String, status: SessionStatus, endedAt: Long, activeSeconds: Int, countedAsSample: Boolean): Boolean {
        var changed = false
        modify(id) { session ->
            if (session.status != SessionStatus.IN_PROGRESS) session
            else {
                changed = true
                session.copy(status = status, endedAt = endedAt, activeSeconds = activeSeconds, countedAsSample = countedAsSample)
            }
        }
        return changed
    }
    override suspend fun updateStatus(id: String, status: SessionStatus) = modify(id) { it.copy(status = status) }
    override suspend fun saveFeedback(id: String, feedback: SessionFeedback) = modify(id) { it.copy(feedback = feedback) }
    override suspend fun delete(id: String) = flow.update { list -> list.filterNot { it.id == id } }

    override val activeState: Flow<ActiveSessionState?> = active
    override suspend fun currentActiveState() = active.value
    override suspend fun saveActiveState(state: ActiveSessionState) {
        active.value = state
    }
    override suspend fun clearActiveState() {
        active.value = null
    }

    override suspend fun replaceAll(sessions: List<WorkoutSession>) {
        flow.value = sessions
    }
    override suspend fun clear() {
        flow.value = emptyList()
        active.value = null
    }
}

class MutexTransactionRunner : TransactionRunner {
    private val mutex = Mutex()
    override suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}

/** Groups every repository so services and the app container can pass them around together. */
class Repositories(
    val userState: UserStateRepository,
    val equipment: EquipmentRepository,
    val exclusions: ExclusionRepository,
    val favorites: FavoritesRepository,
    val programs: ProgramRepository,
    val sessions: SessionRepository,
    val transactions: TransactionRunner,
) {
    companion object {
        fun inMemory() = Repositories(
            userState = InMemoryUserStateRepository(),
            equipment = InMemoryEquipmentRepository(),
            exclusions = InMemoryExclusionRepository(),
            favorites = InMemoryFavoritesRepository(),
            programs = InMemoryProgramRepository(),
            sessions = InMemorySessionRepository(),
            transactions = MutexTransactionRunner(),
        )
    }
}
