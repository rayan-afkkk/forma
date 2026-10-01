package app.forma.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    /** Started and not yet finished. At most one session is in progress at a time. */
    IN_PROGRESS,

    /** Every planned set was completed. */
    COMPLETED,

    /** Finished early or with skipped work. Saved honestly as partial. */
    PARTIAL,
}

@Serializable
enum class Difficulty(val label: String, val description: String) {
    EASY("Easy", "I had plenty left"),
    JUST_RIGHT("Just right", "Challenging but manageable"),
    HARD("Hard", "I struggled to finish"),
    TOO_HARD("Too hard", "I couldn't do what was planned"),
}

/** A set the person actually performed. Stores what was done, not what was planned. */
@Serializable
data class SetLog(
    val sessionId: String,
    val itemKey: String,
    val setNumber: Int,
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val reps: Int? = null,
    val seconds: Int? = null,
    val load: Load? = null,
    val completedAt: Long,
) {
    val id: String get() = setLogId(sessionId, itemKey, setNumber)
}

fun setLogId(sessionId: String, itemKey: String, setNumber: Int) = "$sessionId/$itemKey/$setNumber"

@Serializable
data class SessionFeedback(
    val difficulty: Difficulty?,
    val note: String = "",
    val updatedAt: Long,
)

/**
 * A workout the person started. [plan] is the snapshot of what was planned, including any
 * replacements made during the session; [sets] is what was actually performed.
 *
 * Times are stored as UTC epoch milliseconds. [localDate] and [zoneId] record the calendar day and
 * time zone where the session happened, so history does not shift when the person travels or the
 * device time zone changes.
 */
@Serializable
data class WorkoutSession(
    val id: String,
    val plan: WorkoutPlan,
    val status: SessionStatus,
    val startedAt: Long,
    val endedAt: Long? = null,
    val activeSeconds: Int = 0,
    val zoneId: String,
    val localDate: String,
    val sets: List<SetLog> = emptyList(),
    val skippedItemKeys: Set<String> = emptySet(),
    val feedback: SessionFeedback? = null,
    /** Counted toward the free adaptive-session sample. */
    val countedAsSample: Boolean = false,
) {
    val isFinished: Boolean get() = status != SessionStatus.IN_PROGRESS
    val completedSetCount: Int get() = sets.size
    val plannedSetCount: Int get() = plan.totalSets

    fun setsFor(itemKey: String): List<SetLog> = sets.filter { it.itemKey == itemKey }.sortedBy { it.setNumber }

    fun isItemComplete(item: WorkoutItem): Boolean = setsFor(item.key).size >= item.sets
}

/** Player position and timers. Timers are derived from stored timestamps, never from an in-memory countdown. */
@Serializable
data class TimerState(
    val targetMs: Long,
    val accumulatedMs: Long = 0,
    /** Wall-clock time the timer was last started, or null while stopped. */
    val runningSince: Long? = null,
) {
    val isRunning: Boolean get() = runningSince != null

    fun elapsedMs(now: Long): Long = accumulatedMs + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remainingMs(now: Long): Long = (targetMs - elapsedMs(now)).coerceAtLeast(0)
    fun isDone(now: Long): Boolean = elapsedMs(now) >= targetMs

    fun start(now: Long): TimerState = if (isRunning) this else copy(runningSince = now)
    fun stop(now: Long): TimerState =
        if (!isRunning) this else copy(accumulatedMs = elapsedMs(now), runningSince = null)

    fun extend(byMs: Long): TimerState = copy(targetMs = targetMs + byMs)
}

@Serializable
enum class PlayerPhase { WORK, REST, FINISHED }

@Serializable
data class ActiveSessionState(
    val sessionId: String,
    val itemIndex: Int,
    val setNumber: Int,
    val phase: PlayerPhase,
    /** For time-based sets. */
    val workTimer: TimerState? = null,
    val restTimer: TimerState? = null,
    val paused: Boolean = false,
    val pausedAt: Long? = null,
    val totalPausedMs: Long = 0,
    /** Timers that were running when the session was paused, restarted on resume. */
    val resumeRestTimer: Boolean = false,
    val resumeWorkTimer: Boolean = false,
    val startedAt: Long,
    /** Values the person has dialled in for the current set, kept across interruptions. */
    val draftReps: Int? = null,
    val draftLoad: Load? = null,
    val updatedAt: Long,
) {
    fun activeMs(now: Long): Long {
        val pausedNow = if (paused && pausedAt != null) (now - pausedAt).coerceAtLeast(0) else 0
        return (now - startedAt - totalPausedMs - pausedNow).coerceAtLeast(0)
    }
}
