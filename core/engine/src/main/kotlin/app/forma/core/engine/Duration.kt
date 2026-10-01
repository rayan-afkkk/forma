package app.forma.core.engine

import app.forma.core.model.NoteKind
import app.forma.core.model.Phase
import app.forma.core.model.PlanNote
import app.forma.core.model.Target
import app.forma.core.model.WorkoutItem
import kotlin.math.roundToInt

/** Rough, transparent duration estimates. Shown to people as "about N min". */
object DurationEstimator {

    fun workSeconds(item: WorkoutItem): Double {
        val perSet = when (val t = item.target) {
            is Target.Reps -> (t.min + t.max) / 2.0 * TrainingRules.SECONDS_PER_REP
            is Target.Time -> t.seconds.toDouble()
        }
        return perSet * (if (item.perSide) 2 else 1)
    }

    fun itemSeconds(item: WorkoutItem): Int {
        val work = workSeconds(item) * item.sets
        val rest = item.restSeconds * (item.sets - 1).coerceAtLeast(0)
        return (work + rest).roundToInt()
    }

    fun totalSeconds(items: List<WorkoutItem>): Int {
        if (items.isEmpty()) return 0
        return items.sumOf { itemSeconds(it) } + TrainingRules.TRANSITION_SECONDS * (items.size - 1)
    }
}

sealed interface FitResult {
    val items: List<WorkoutItem>
    val notes: List<PlanNote>

    data class Fits(override val items: List<WorkoutItem>, override val notes: List<PlanNote>) : FitResult

    /** The requested time is too short for a meaningful version of this session. */
    data class Impractical(
        override val items: List<WorkoutItem>,
        override val notes: List<PlanNote>,
        val requestedMinutes: Int,
        val shortestMinutes: Int,
    ) : FitResult
}

/**
 * Shortens a workout to fit a time budget, in a fixed, explainable order:
 * optional main work, optional warm-up/cool-down, extra sets, shorter rests, then the lowest-priority
 * main exercises (always keeping at least [TrainingRules.MIN_MAIN_ITEMS_WHEN_SHORTENED]).
 */
object DurationFitter {

    fun fit(items: List<WorkoutItem>, targetMinutes: Int): FitResult {
        val budget = targetMinutes * 60 * (1 + TrainingRules.DURATION_TOLERANCE)
        var current = items
        if (DurationEstimator.totalSeconds(current) <= budget) return FitResult.Fits(current, emptyList())

        val removed = mutableListOf<String>()
        val steps = mutableListOf<String>()

        fun fits() = DurationEstimator.totalSeconds(current) <= budget

        // 1. Optional main work, last first.
        for (item in current.filter { it.optional && it.phase == Phase.MAIN }.reversed()) {
            if (fits()) break
            current = current - item
            removed += item.exerciseName
        }
        // 2. Optional warm-up and cool-down items.
        for (item in current.filter { it.optional && it.phase != Phase.MAIN }.reversed()) {
            if (fits()) break
            current = current - item
            removed += item.exerciseName
        }
        // 3. Main exercises down to 2 sets.
        if (!fits() && current.any { it.phase == Phase.MAIN && it.sets > 2 }) {
            current = current.map { if (it.phase == Phase.MAIN && it.sets > 2) it.copy(sets = 2) else it }
            steps += "main exercises use 2 sets"
        }
        // 4. Shorter rests.
        if (!fits() && current.any { it.phase == Phase.MAIN && it.restSeconds > TrainingRules.MIN_MAIN_REST_SECONDS }) {
            current = current.map {
                if (it.phase == Phase.MAIN) it.copy(restSeconds = it.restSeconds.coerceAtMost(TrainingRules.MIN_MAIN_REST_SECONDS)) else it
            }
            steps += "rests are shorter"
        }
        // 5. Drop lowest-priority main exercises (from the end), keeping a minimum.
        while (!fits()) {
            val mains = current.filter { it.phase == Phase.MAIN }
            if (mains.size <= TrainingRules.MIN_MAIN_ITEMS_WHEN_SHORTENED) break
            val drop = mains.last()
            current = current - drop
            removed += drop.exerciseName
        }
        // 6. Remaining non-essential warm-up/cool-down beyond one each.
        while (!fits()) {
            val extras = current.filter { it.phase != Phase.MAIN }
                .groupBy { it.phase }.values.filter { it.size > 1 }.flatten()
            if (extras.isEmpty()) break
            val drop = extras.last()
            current = current - drop
            removed += drop.exerciseName
        }

        val parts = mutableListOf<String>()
        if (removed.isNotEmpty()) parts += "left out ${removed.joinToString(", ")}"
        parts += steps
        val minutes = ((DurationEstimator.totalSeconds(current) + 30) / 60).coerceAtLeast(1)
        val note = PlanNote(NoteKind.SHORTENED, "Shortened to about $minutes min: ${parts.joinToString("; ")}.")

        return if (fits()) {
            FitResult.Fits(current, listOf(note))
        } else {
            FitResult.Impractical(
                items = current,
                notes = listOf(
                    PlanNote(
                        NoteKind.SHORTENED,
                        "A $targetMinutes-minute version wouldn't leave room for meaningful main work. " +
                            "The shortest practical version of this session is about $minutes minutes.",
                    ),
                ),
                requestedMinutes = targetMinutes,
                shortestMinutes = minutes,
            )
        }
    }
}
