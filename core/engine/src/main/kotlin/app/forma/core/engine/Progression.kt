package app.forma.core.engine

import app.forma.core.model.ChangeKind
import app.forma.core.model.Difficulty
import app.forma.core.model.Exercise
import app.forma.core.model.ItemChange
import app.forma.core.model.Load
import app.forma.core.model.Target
import app.forma.core.model.format

/** The prescription the generator is about to use for one exercise. */
data class Prescription(
    val exercise: Exercise,
    val sets: Int,
    val maxSets: Int,
    val target: Target,
    val load: Load?,
    /** The slot's starting target, used when a heavier weight resets the rep range. */
    val baseTarget: Target = target,
)

data class ProgressionResult(
    val prescription: Prescription,
    val change: ItemChange?,
)

enum class Direction { PROGRESS, HOLD, REDUCE }

/**
 * Deterministic progression based on what was actually performed and how the session felt.
 * See docs/TRAINING_RULES.md for the plain-language version of these rules.
 *
 * - Too hard, or below target twice in a row -> reduce (lighter, easier variation, or a smaller target).
 * - Below target once, or rated hard -> hold.
 * - Every set at the top of the target and not rated hard -> a small progression.
 * - Otherwise -> hold and aim for a little more.
 */
class ProgressionEngine(
    private val catalog: ContentCatalog,
) {

    fun direction(exposures: List<Exposure>): Direction {
        val attempted = exposures.filter { it.outcome != Exposure.Outcome.NOT_DONE }
        val last = attempted.firstOrNull() ?: return Direction.HOLD
        val previous = attempted.getOrNull(1)
        return when {
            last.difficulty == Difficulty.TOO_HARD -> Direction.REDUCE
            last.outcome == Exposure.Outcome.BELOW && previous?.outcome == Exposure.Outcome.BELOW -> Direction.REDUCE
            last.outcome == Exposure.Outcome.BELOW -> Direction.HOLD
            last.difficulty == Difficulty.HARD -> Direction.HOLD
            last.outcome == Exposure.Outcome.HIT_TOP -> Direction.PROGRESS
            else -> Direction.HOLD
        }
    }

    /**
     * Applies one progression step to [base] using [exposures] of the same exercise (newest first).
     * [allowIncrease] is false when automatic progression is not available (free tier after the sample);
     * reductions still apply so the plan never stays too hard.
     */
    fun apply(
        base: Prescription,
        exposures: List<Exposure>,
        loads: List<Load>,
        constraints: Constraints,
        usedElsewhere: Set<String>,
        allowIncrease: Boolean,
    ): ProgressionResult {
        val last = exposures.firstOrNull { it.outcome != Exposure.Outcome.NOT_DONE }
        // Carry forward the last performed load and target so repeated sessions build on each other.
        val carried = if (last != null) {
            base.copy(
                load = last.performedLoad?.let { EquipmentLoads.snap(loads, it) } ?: base.load,
                target = carryTarget(base.target, last.item.target),
                sets = last.item.programmedSets.coerceIn(1, base.maxSets),
            )
        } else {
            base
        }
        if (last == null) {
            val message = if (base.load != null) {
                "First time with this exercise: start with a weight that lets you finish every set with good form."
            } else {
                "First time with this exercise: move at a comfortable pace and adjust as you go."
            }
            return ProgressionResult(base, ItemChange(ChangeKind.FIRST_TIME, message, TrainingRules.VERSION))
        }
        return when (direction(exposures)) {
            Direction.PROGRESS -> if (allowIncrease) progress(carried, last, loads, constraints, usedElsewhere)
            else ProgressionResult(carried, null)
            Direction.REDUCE -> reduce(carried, last, loads, constraints, usedElsewhere)
            Direction.HOLD -> ProgressionResult(carried, hold(last))
        }
    }

    private fun carryTarget(base: Target, last: Target): Target = when {
        base is Target.Reps && last is Target.Reps -> last
        base is Target.Time && last is Target.Time -> last
        else -> base
    }

    private fun hold(last: Exposure): ItemChange? {
        val text = when {
            last.difficulty == Difficulty.HARD -> "You rated your last session hard, so this stays the same today."
            last.outcome == Exposure.Outcome.BELOW -> "Same targets as last time. Aim to complete every set."
            else -> return null
        }
        return ItemChange(ChangeKind.HOLD, text, TrainingRules.VERSION)
    }

    private fun progress(
        p: Prescription,
        last: Exposure,
        loads: List<Load>,
        constraints: Constraints,
        used: Set<String>,
    ): ProgressionResult {
        val target = p.target
        val loaded = p.exercise.equipment.dumbbells != null
        // 1. Heavier dumbbell, if a reasonable jump exists.
        if (loaded && p.load != null) {
            val next = EquipmentLoads.nextUp(loads, p.load)
            if (next != null && EquipmentLoads.isReasonableJump(p.load, next)) {
                // With a heavier weight, return any extended rep range to the slot's starting range.
                val newTarget = if (target is Target.Reps && p.baseTarget is Target.Reps) p.baseTarget else target
                return result(
                    p.copy(load = next, target = newTarget), ChangeKind.LOAD_UP,
                    "You reached the top of your target with ${p.load.format()} last time, so today uses ${next.format()}.",
                )
            }
        }
        // 2. Time-based holds get a little longer, up to a cap.
        if (target is Target.Time && target.seconds < TrainingRules.TIME_CAP_SECONDS) {
            val newSeconds = (target.seconds + TrainingRules.TIME_STEP_SECONDS).coerceAtMost(TrainingRules.TIME_CAP_SECONDS)
            return result(
                p.copy(target = Target.Time(newSeconds)), ChangeKind.MORE_TIME,
                "You held for the full ${target.seconds} seconds last time, so today's target is $newSeconds seconds.",
            )
        }
        // 3. Bodyweight: a harder variation first.
        if (!loaded || p.load == null) {
            harderVariation(p.exercise, constraints, used)?.let { harder ->
                return result(
                    baseFor(harder, p), ChangeKind.HARDER_VARIATION,
                    "${p.exercise.name} has become comfortable, so today moves on to ${harder.name}.",
                )
            }
        }
        // 4. Reps: extend the range up to a cap.
        if (target is Target.Reps) {
            val cap = if (loaded) TrainingRules.REP_CAP_LOADED else TrainingRules.REP_CAP_BODYWEIGHT
            if (target.max < cap) {
                val newMax = (target.max + TrainingRules.REP_EXTENSION).coerceAtMost(cap)
                val newMin = (target.min + TrainingRules.REP_EXTENSION).coerceAtMost(newMax)
                val why = if (loaded && p.load != null) {
                    val next = EquipmentLoads.nextUp(loads, p.load)
                    if (next == null) "You're using your heaviest available weight, so today aims for a few more reps."
                    else "The next weight up (${next.format()}) is a big jump, so today aims for a few more reps instead."
                } else {
                    "You reached the top of your rep target, so today aims for a few more."
                }
                return result(p.copy(target = Target.Reps(newMin, newMax)), ChangeKind.MORE_REPS, why)
            }
        }
        // 5. One more set, up to the slot's limit.
        if (p.sets < p.maxSets) {
            return result(
                p.copy(sets = p.sets + 1), ChangeKind.EXTRA_SET,
                "You completed every set comfortably, so today adds one more set.",
            )
        }
        // 6. Loaded and nothing else left: a harder variation.
        harderVariation(p.exercise, constraints, used)?.let { harder ->
            return result(
                baseFor(harder, p), ChangeKind.HARDER_VARIATION,
                "${p.exercise.name} has become comfortable, so today moves on to ${harder.name}.",
            )
        }
        return result(
            p, ChangeKind.HOLD,
            "You've reached the top of what this exercise can offer with your equipment. Today keeps the same targets.",
        )
    }

    private fun reduce(
        p: Prescription,
        last: Exposure,
        loads: List<Load>,
        constraints: Constraints,
        used: Set<String>,
    ): ProgressionResult {
        val why = if (last.difficulty == Difficulty.TOO_HARD) "Your last session felt too hard"
        else "The last two times were below target"
        if (p.load != null) {
            EquipmentLoads.nextDown(loads, p.load)?.let { lighter ->
                return result(p.copy(load = lighter), ChangeKind.LOAD_DOWN, "$why, so today uses ${lighter.format()}.")
            }
        }
        easierVariation(p.exercise, constraints, used)?.let { easier ->
            return result(baseFor(easier, p), ChangeKind.EASIER_VARIATION, "$why, so today uses ${easier.name} instead.")
        }
        return when (val target = p.target) {
            is Target.Reps -> {
                val newMin = (target.min - 2).coerceAtLeast(TrainingRules.REP_FLOOR)
                val newMax = (target.max - 2).coerceAtLeast(newMin)
                if (newMin == target.min && newMax == target.max) {
                    if (p.sets > 1) result(p.copy(sets = p.sets - 1), ChangeKind.FEWER_SETS, "$why, so today has one fewer set.")
                    else ProgressionResult(p, null)
                } else {
                    result(p.copy(target = Target.Reps(newMin, newMax)), ChangeKind.FEWER_REPS, "$why, so today's rep target is lower.")
                }
            }
            is Target.Time -> {
                val newSeconds = (target.seconds - TrainingRules.TIME_STEP_SECONDS).coerceAtLeast(TrainingRules.TIME_FLOOR_SECONDS)
                if (newSeconds == target.seconds) ProgressionResult(p, null)
                else result(p.copy(target = Target.Time(newSeconds)), ChangeKind.LESS_TIME, "$why, so today's hold is $newSeconds seconds.")
            }
        }
    }

    fun harderVariation(exercise: Exercise, constraints: Constraints, used: Set<String>): Exercise? =
        catalog.family(exercise.family)
            .filter { it.familyRank > exercise.familyRank && it.id !in used && constraints.allows(it) }
            .filter { it.phases.intersect(exercise.phases).isNotEmpty() }
            .minByOrNull { it.familyRank }

    fun easierVariation(exercise: Exercise, constraints: Constraints, used: Set<String>): Exercise? =
        catalog.family(exercise.family)
            .filter { it.familyRank < exercise.familyRank && it.id !in used && constraints.allows(it) }
            .filter { it.phases.intersect(exercise.phases).isNotEmpty() }
            .maxByOrNull { it.familyRank }

    /** A fresh prescription for a different exercise in the same slot. Load is chosen by the generator. */
    private fun baseFor(exercise: Exercise, from: Prescription): Prescription = Prescription(
        exercise = exercise,
        sets = from.sets,
        maxSets = from.maxSets,
        target = defaultTarget(exercise),
        load = null,
        baseTarget = defaultTarget(exercise),
    )

    private fun result(p: Prescription, kind: ChangeKind, message: String) =
        ProgressionResult(p, ItemChange(kind, message, TrainingRules.VERSION))

    companion object {
        fun defaultTarget(exercise: Exercise): Target = when (exercise.execution) {
            app.forma.core.model.Execution.REPS -> Target.Reps(exercise.defaultRepsMin, exercise.defaultRepsMax)
            app.forma.core.model.Execution.TIME -> Target.Time(exercise.defaultSeconds)
        }
    }
}
