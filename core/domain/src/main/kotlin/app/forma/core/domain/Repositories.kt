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

/** Preferences and small state (DataStore on Android). */
interface UserStateRepository {
    val state: Flow<UserState>
    suspend fun current(): UserState
    suspend fun update(transform: (UserState) -> UserState): UserState
    suspend fun clear()
}

interface EquipmentRepository {
    val profiles: Flow<List<EquipmentProfile>>
    suspend fun all(): List<EquipmentProfile>
    suspend fun upsert(profile: EquipmentProfile)
    suspend fun delete(id: String)
    suspend fun clear()
}

/** Permanent exclusions. Kept until the person restores the exercise. */
interface ExclusionRepository {
    val exclusions: Flow<List<ExerciseExclusion>>
    suspend fun all(): List<ExerciseExclusion>
    suspend fun add(exerciseId: ExerciseId, createdAt: Long)
    suspend fun remove(exerciseId: ExerciseId)
    suspend fun clear()
}

interface FavoritesRepository {
    val favorites: Flow<Set<String>>
    suspend fun all(): Set<String>
    suspend fun set(templateId: String, favorite: Boolean)
    suspend fun clear()
}

interface ProgramRepository {
    val enrollment: Flow<ProgramEnrollment?>
    suspend fun currentEnrollment(): ProgramEnrollment?
    suspend fun setEnrollment(enrollment: ProgramEnrollment?)

    val moves: Flow<List<ScheduleMove>>
    suspend fun allMoves(): List<ScheduleMove>
    suspend fun addMove(move: ScheduleMove)

    /** One-day plan adjustments keyed by local date and plan key. */
    fun adjustments(date: String, planKey: String): Flow<PlanAdjustments>
    suspend fun currentAdjustments(date: String, planKey: String): PlanAdjustments
    suspend fun setAdjustments(date: String, planKey: String, adjustments: PlanAdjustments)
    suspend fun clear()
}

interface SessionRepository {
    /** All sessions, newest first, including an in-progress one. */
    val sessions: Flow<List<WorkoutSession>>
    suspend fun all(): List<WorkoutSession>
    fun observe(id: String): Flow<WorkoutSession?>
    suspend fun get(id: String): WorkoutSession?

    suspend fun insert(session: WorkoutSession)
    suspend fun updatePlan(id: String, plan: WorkoutPlan)

    /** Inserts or replaces one set. Keyed by session, item and set number, so retries never duplicate. */
    suspend fun upsertSet(set: SetLog)
    suspend fun deleteSet(sessionId: String, itemKey: String, setNumber: Int)
    suspend fun setSkipped(id: String, itemKeys: Set<String>)

    /**
     * Marks an in-progress session finished. Returns false and changes nothing if it was already
     * finished, so repeated taps or retries record completion once.
     */
    suspend fun finish(id: String, status: SessionStatus, endedAt: Long, activeSeconds: Int, countedAsSample: Boolean): Boolean

    /** Updates the status of an already finished session after its sets are edited. */
    suspend fun updateStatus(id: String, status: SessionStatus)
    suspend fun saveFeedback(id: String, feedback: SessionFeedback)
    suspend fun delete(id: String)

    val activeState: Flow<ActiveSessionState?>
    suspend fun currentActiveState(): ActiveSessionState?
    suspend fun saveActiveState(state: ActiveSessionState)
    suspend fun clearActiveState()

    /** Replaces all sessions (backup import). */
    suspend fun replaceAll(sessions: List<WorkoutSession>)
    suspend fun clear()
}

/** Runs a block atomically (a Room transaction on Android, a mutex in memory). */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
