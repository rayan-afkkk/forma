package app.forma.android.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.withTransaction
import app.forma.core.domain.BackupCodec
import app.forma.core.domain.EquipmentRepository
import app.forma.core.domain.ExclusionRepository
import app.forma.core.domain.FavoritesRepository
import app.forma.core.domain.ProgramRepository
import app.forma.core.domain.SessionRepository
import app.forma.core.domain.TransactionRunner
import app.forma.core.domain.UserStateRepository
import app.forma.core.model.ActiveSessionState
import app.forma.core.model.Difficulty
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExerciseExclusion
import app.forma.core.model.ExerciseId
import app.forma.core.model.Load
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.ProgramEnrollment
import app.forma.core.model.ScheduleMove
import app.forma.core.model.SessionFeedback
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.UserState
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutPlan
import app.forma.core.model.WorkoutSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.builtins.serializer

/** Same JSON settings as backups (covered by JVM tests), so stored and exported data match. */
private val json = BackupCodec.json
private const val TAG = "FormaData"

class RoomTransactionRunner(private val db: FormaDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}

// ------------------------------------------------------------------------------------------ user state

/**
 * Preferences and small state in DataStore, stored as one JSON document so updates are atomic.
 * Unknown fields from newer versions are ignored and missing fields take their defaults.
 */
class DataStoreUserStateRepository(private val store: DataStore<Preferences>) : UserStateRepository {
    private val key = stringPreferencesKey("user_state_v1")

    private fun decode(text: String?): UserState = text?.let {
        runCatching { json.decodeFromString(UserState.serializer(), it) }
            .onFailure { e -> Log.e(TAG, "Unreadable preferences; using defaults", e) }
            .getOrNull()
    } ?: UserState()

    override val state: Flow<UserState> = store.data.map { decode(it[key]) }.distinctUntilChanged()

    override suspend fun current(): UserState = state.first()

    override suspend fun update(transform: (UserState) -> UserState): UserState {
        var result = UserState()
        store.edit { prefs ->
            result = transform(decode(prefs[key]))
            prefs[key] = json.encodeToString(UserState.serializer(), result)
        }
        return result
    }

    override suspend fun clear() {
        store.edit { it.remove(key) }
    }
}

// ------------------------------------------------------------------------------------------ equipment

class RoomEquipmentRepository(private val dao: EquipmentDao, private val now: () -> Long) : EquipmentRepository {
    private val itemsSerializer = ListSerializer(EquipmentItem.serializer())

    private fun EquipmentProfileEntity.toModel() = EquipmentProfile(
        id = id, name = name, unit = WeightUnit.valueOf(unit),
        items = json.decodeFromString(itemsSerializer, itemsJson),
    )

    override val profiles: Flow<List<EquipmentProfile>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    override suspend fun all() = dao.all().map { it.toModel() }
    override suspend fun upsert(profile: EquipmentProfile) = dao.upsert(
        EquipmentProfileEntity(profile.id, profile.name, profile.unit.name, json.encodeToString(itemsSerializer, profile.items), now()),
    )
    override suspend fun delete(id: String) = dao.delete(id)
    override suspend fun clear() = dao.clear()
}

class RoomExclusionRepository(private val dao: ExclusionDao) : ExclusionRepository {
    override val exclusions: Flow<List<ExerciseExclusion>> =
        dao.observeAll().map { list -> list.map { ExerciseExclusion(it.exerciseId, it.createdAt) } }
    override suspend fun all() = dao.all().map { ExerciseExclusion(it.exerciseId, it.createdAt) }
    override suspend fun add(exerciseId: ExerciseId, createdAt: Long) = dao.insert(ExclusionEntity(exerciseId, createdAt))
    override suspend fun remove(exerciseId: ExerciseId) = dao.delete(exerciseId)
    override suspend fun clear() = dao.clear()
}

class RoomFavoritesRepository(private val dao: FavoriteDao, private val now: () -> Long) : FavoritesRepository {
    override val favorites: Flow<Set<String>> = dao.observeIds().map { it.toSet() }
    override suspend fun all() = dao.ids().toSet()
    override suspend fun set(templateId: String, favorite: Boolean) =
        if (favorite) dao.upsert(FavoriteEntity(templateId, now())) else dao.delete(templateId)
    override suspend fun clear() = dao.clear()
}

class RoomProgramRepository(private val dao: ProgramDao) : ProgramRepository {
    override val enrollment: Flow<ProgramEnrollment?> =
        dao.observeEnrollment().map { it?.let { e -> ProgramEnrollment(e.programId, e.startedAt, e.rotationIndex) } }
    override suspend fun currentEnrollment() = dao.enrollment()?.let { ProgramEnrollment(it.programId, it.startedAt, it.rotationIndex) }
    override suspend fun setEnrollment(enrollment: ProgramEnrollment?) {
        if (enrollment == null) dao.clearEnrollment()
        else dao.upsertEnrollment(EnrollmentEntity(1, enrollment.programId, enrollment.startedAt, enrollment.rotationIndex))
    }

    override val moves: Flow<List<ScheduleMove>> = dao.observeMoves().map { list -> list.map { ScheduleMove(it.fromDate, it.toDate) } }
    override suspend fun allMoves() = dao.moves().map { ScheduleMove(it.fromDate, it.toDate) }
    override suspend fun addMove(move: ScheduleMove) = dao.upsertMove(ScheduleMoveEntity(move.fromDate, move.toDate))

    private fun decode(text: String?) =
        text?.let { runCatching { json.decodeFromString(PlanAdjustments.serializer(), it) }.getOrNull() } ?: PlanAdjustments()

    override fun adjustments(date: String, planKey: String): Flow<PlanAdjustments> =
        dao.observeAdjustments(date, planKey).map(::decode).distinctUntilChanged()
    override suspend fun currentAdjustments(date: String, planKey: String) = decode(dao.adjustments(date, planKey))
    override suspend fun setAdjustments(date: String, planKey: String, adjustments: PlanAdjustments) {
        dao.pruneAdjustments(date)
        dao.upsertAdjustments(PlanAdjustmentEntity(date, planKey, json.encodeToString(PlanAdjustments.serializer(), adjustments)))
    }

    override suspend fun clear() {
        dao.clearEnrollment()
        dao.clearMoves()
        dao.clearAdjustments()
    }
}

// ------------------------------------------------------------------------------------------ sessions

class RoomSessionRepository(private val dao: SessionDao) : SessionRepository {
    private val keysSerializer = SetSerializer(String.serializer())

    private fun SessionWithSets.toModel(): WorkoutSession {
        val s = session
        return WorkoutSession(
            id = s.id,
            plan = json.decodeFromString(WorkoutPlan.serializer(), s.planJson),
            status = SessionStatus.valueOf(s.status),
            startedAt = s.startedAt,
            endedAt = s.endedAt,
            activeSeconds = s.activeSeconds,
            zoneId = s.zoneId,
            localDate = s.localDate,
            sets = sets.map { it.toModel() }.sortedWith(compareBy({ it.completedAt }, { it.setNumber })),
            skippedItemKeys = json.decodeFromString(keysSerializer, s.skippedKeysJson),
            feedback = s.feedbackUpdatedAt?.let {
                SessionFeedback(s.feedbackDifficulty?.let { d -> Difficulty.valueOf(d) }, s.feedbackNote.orEmpty(), it)
            },
            countedAsSample = s.countedAsSample,
        )
    }

    private fun SetLogEntity.toModel() = SetLog(
        sessionId = sessionId, itemKey = itemKey, setNumber = setNumber, exerciseId = exerciseId, exerciseName = exerciseName,
        reps = reps, seconds = seconds,
        load = if (loadHundredths != null && loadUnit != null) Load(loadHundredths, WeightUnit.valueOf(loadUnit)) else null,
        completedAt = completedAt,
    )

    private fun SetLog.toEntity() = SetLogEntity(
        sessionId, itemKey, setNumber, exerciseId, exerciseName, reps, seconds, load?.hundredths, load?.unit?.name, completedAt,
    )

    private fun WorkoutSession.toEntity() = SessionEntity(
        id = id,
        planJson = json.encodeToString(WorkoutPlan.serializer(), plan),
        status = status.name,
        startedAt = startedAt,
        endedAt = endedAt,
        activeSeconds = activeSeconds,
        zoneId = zoneId,
        localDate = localDate,
        skippedKeysJson = json.encodeToString(keysSerializer, skippedItemKeys),
        feedbackDifficulty = feedback?.difficulty?.name,
        feedbackNote = feedback?.note,
        feedbackUpdatedAt = feedback?.updatedAt,
        countedAsSample = countedAsSample,
        ruleVersion = plan.ruleVersion,
    )

    override val sessions: Flow<List<WorkoutSession>> = dao.observeAll().map { list -> list.map { it.toModel() } }
    override suspend fun all() = dao.all().map { it.toModel() }
    override fun observe(id: String): Flow<WorkoutSession?> = dao.observe(id).map { it?.toModel() }
    override suspend fun get(id: String) = dao.get(id)?.toModel()

    override suspend fun insert(session: WorkoutSession) {
        dao.insert(session.toEntity())
        if (session.sets.isNotEmpty()) dao.insertSets(session.sets.map { it.toEntity() })
    }

    override suspend fun updatePlan(id: String, plan: WorkoutPlan) = dao.updatePlan(id, json.encodeToString(WorkoutPlan.serializer(), plan))
    override suspend fun upsertSet(set: SetLog) = dao.upsertSet(set.toEntity())
    override suspend fun deleteSet(sessionId: String, itemKey: String, setNumber: Int) = dao.deleteSet(sessionId, itemKey, setNumber)
    override suspend fun setSkipped(id: String, itemKeys: Set<String>) = dao.updateSkipped(id, json.encodeToString(keysSerializer, itemKeys))

    override suspend fun finish(id: String, status: SessionStatus, endedAt: Long, activeSeconds: Int, countedAsSample: Boolean): Boolean =
        dao.finish(id, status.name, endedAt, activeSeconds, countedAsSample) == 1

    override suspend fun updateStatus(id: String, status: SessionStatus) = dao.updateStatus(id, status.name)
    override suspend fun saveFeedback(id: String, feedback: SessionFeedback) =
        dao.updateFeedback(id, feedback.difficulty?.name, feedback.note, feedback.updatedAt)
    override suspend fun delete(id: String) = dao.delete(id)

    private fun ActiveSessionEntity.toModel(): ActiveSessionState? =
        runCatching { json.decodeFromString(ActiveSessionState.serializer(), this.json) }.getOrNull()

    override val activeState: Flow<ActiveSessionState?> = dao.observeActive().map { it?.toModel() }
    override suspend fun currentActiveState() = dao.active()?.toModel()
    override suspend fun saveActiveState(state: ActiveSessionState) =
        dao.upsertActive(ActiveSessionEntity(1, state.sessionId, json.encodeToString(ActiveSessionState.serializer(), state), state.updatedAt))
    override suspend fun clearActiveState() = dao.clearActive()

    override suspend fun replaceAll(sessions: List<WorkoutSession>) {
        dao.clear()
        dao.insertAll(sessions.map { it.toEntity() })
        dao.insertSets(sessions.flatMap { s -> s.sets.map { it.toEntity() } })
    }

    override suspend fun clear() {
        dao.clear()
        dao.clearActive()
    }
}
