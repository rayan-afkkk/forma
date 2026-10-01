package app.forma.core.engine

import app.forma.core.model.Difficulty
import app.forma.core.model.ExerciseId
import app.forma.core.model.Execution
import app.forma.core.model.Load
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.Target
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutSession

/**
 * One time an exercise appeared in a finished session: what was planned and what was actually done.
 * Missed sessions never create exposures, so they never count as completed work.
 */
data class Exposure(
    val sessionId: String,
    val localDate: String,
    val endedAt: Long,
    val sessionTemplateId: String,
    val item: WorkoutItem,
    val sets: List<SetLog>,
    val skipped: Boolean,
    val difficulty: Difficulty?,
) {
    val performedLoad: Load?
        get() = sets.mapNotNull { it.load }.maxOrNull() ?: item.load

    val outcome: Outcome
        get() = evaluate(item, sets, skipped)

    enum class Outcome {
        /** Not attempted (skipped or session ended first). Carries no information. */
        NOT_DONE,

        /** All planned sets done at or above the top of the target. */
        HIT_TOP,

        /** All planned sets done within the target. */
        IN_RANGE,

        /** Fewer sets than planned, or reps/time below the target. */
        BELOW,
    }

    companion object {
        fun evaluate(item: WorkoutItem, sets: List<SetLog>, skipped: Boolean): Outcome {
            if (sets.isEmpty()) return Outcome.NOT_DONE
            val allSets = sets.size >= item.sets && !skipped
            return when (val target = item.target) {
                is Target.Reps -> {
                    val reps = sets.map { it.reps ?: 0 }
                    when {
                        !allSets || reps.any { it < target.min } -> Outcome.BELOW
                        reps.all { it >= target.max } -> Outcome.HIT_TOP
                        else -> Outcome.IN_RANGE
                    }
                }
                is Target.Time -> {
                    val secs = sets.map { it.seconds ?: 0 }
                    when {
                        !allSets || secs.any { it < target.seconds * TrainingRules.TIME_BELOW_FRACTION } -> Outcome.BELOW
                        secs.all { it >= target.seconds } -> Outcome.HIT_TOP
                        else -> Outcome.IN_RANGE
                    }
                }
            }
        }
    }
}

/** Finished-session history, newest first, as the engine sees it. */
class TrainingHistory(exposures: List<Exposure>) {
    val exposures: List<Exposure> = exposures.sortedByDescending { it.endedAt }

    val isEmpty: Boolean get() = exposures.isEmpty()

    fun forExercise(id: ExerciseId): List<Exposure> = exposures.filter { it.item.exerciseId == id }

    /** Most recent exercise used for this slot of this session template, for continuity. */
    fun lastForSlot(sessionTemplateId: String, slotId: String): Exposure? =
        exposures.firstOrNull { it.sessionTemplateId == sessionTemplateId && it.item.slotId == slotId && !it.skipped }

    companion object {
        val Empty = TrainingHistory(emptyList())

        /** Builds history from finished sessions. In-progress sessions are ignored. */
        fun from(sessions: List<WorkoutSession>): TrainingHistory = TrainingHistory(
            sessions.filter { it.status != SessionStatus.IN_PROGRESS }.flatMap { session ->
                session.plan.items.map { item ->
                    val sets = session.setsFor(item.key)
                    Exposure(
                        sessionId = session.id,
                        localDate = session.localDate,
                        endedAt = session.endedAt ?: session.startedAt,
                        sessionTemplateId = session.plan.source.sessionTemplateId,
                        item = item,
                        sets = sets,
                        skipped = item.key in session.skippedItemKeys && sets.isEmpty(),
                        difficulty = session.feedback?.difficulty,
                    )
                }
            },
        )
    }
}

internal fun WorkoutItem.isLoaded(): Boolean = dumbbellUse != null
internal fun WorkoutItem.isTimed(): Boolean = execution == Execution.TIME
