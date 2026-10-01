package app.forma.core.engine

import app.forma.core.model.ChangeKind
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Execution
import app.forma.core.model.Exercise
import app.forma.core.model.ExerciseId
import app.forma.core.model.ItemChange
import app.forma.core.model.Load
import app.forma.core.model.Target
import app.forma.core.model.WorkoutItem
import app.forma.core.model.format

enum class Relation(val label: String) { EASIER("Easier"), SIMILAR("Similar"), HARDER("Harder") }

data class ReplacementOption(
    val exercise: Exercise,
    val reason: String,
    val relation: Relation,
)

sealed interface ReplacementResult {
    data class Available(val options: List<ReplacementOption>) : ReplacementResult

    /** Nothing suitable. [explanation] says why; the person can leave the exercise out instead. */
    data class None(val explanation: String) : ReplacementResult
}

sealed interface EasierOption {
    val description: String

    data class Variation(val exercise: Exercise) : EasierOption {
        override val description get() = "Switch to ${exercise.name}"
    }

    data class Lighter(val load: Load) : EasierOption {
        override val description get() = "Use ${load.format()}"
    }

    data class SmallerTarget(val target: Target) : EasierOption {
        override val description
            get() = when (target) {
                is Target.Reps -> "Aim for ${target.min}–${target.max} reps"
                is Target.Time -> "Hold for ${target.seconds} seconds"
            }
    }

    data class FewerSets(val sets: Int) : EasierOption {
        override val description get() = "Do $sets ${if (sets == 1) "set" else "sets"}"
    }

    data class None(override val description: String) : EasierOption
}

/**
 * Replacement and "make easier" logic. Replacements preserve the training role where possible:
 * reviewed substitutes first, then the same variation family, then the same movement pattern.
 * Exercises already in the workout are skipped so the rest of the session stays balanced.
 */
class Substitutions(
    private val catalog: ContentCatalog,
    private val progression: ProgressionEngine,
) {

    fun replacements(item: WorkoutItem, otherExerciseIds: Set<ExerciseId>, constraints: Constraints): ReplacementResult {
        val current = catalog.exercise(item.exerciseId) ?: return ReplacementResult.None("This exercise is no longer in the catalog.")
        val candidates = linkedMapOf<ExerciseId, Pair<Exercise, String>>()

        item.replacedFromId?.let { id -> catalog.exercise(id)?.let { candidates[id] = it to "The original exercise" } }
        for (id in current.substitutes) {
            catalog.exercise(id)?.let { candidates.putIfAbsent(id, it to "Reviewed alternative") }
        }
        for (ex in catalog.family(current.family)) {
            candidates.putIfAbsent(ex.id, ex to if (ex.familyRank < current.familyRank) "Easier variation" else "Harder variation")
        }
        for (ex in catalog.exercises.filter { it.pattern == current.pattern && it.phases.intersect(current.phases).isNotEmpty() }) {
            candidates.putIfAbsent(ex.id, ex to "Same movement: ${current.pattern.label.lowercase()}")
        }
        candidates.remove(current.id)

        val rejected = linkedMapOf<Exercise, List<Violation>>()
        val options = candidates.values.mapNotNull { (exercise, reason) ->
            if (exercise.id in otherExerciseIds) return@mapNotNull null
            val violations = constraints.violations(exercise)
            if (violations.isNotEmpty()) {
                rejected[exercise] = violations
                null
            } else {
                ReplacementOption(exercise, reason, relationTo(current, exercise))
            }
        }
        return if (options.isNotEmpty()) {
            ReplacementResult.Available(options)
        } else {
            val why = if (rejected.isEmpty()) {
                "The other options for this movement are already in today's workout."
            } else {
                explainRejections(current.pattern.label, rejected).replace("No ", "No other ")
            }
            ReplacementResult.None("$why You can leave ${current.name} out of this workout instead.")
        }
    }

    /** Builds the replaced item for this workout only. The original is remembered so it returns next time. */
    fun applyReplacement(
        item: WorkoutItem,
        replacement: Exercise,
        equipment: EquipmentProfile,
        history: TrainingHistory,
    ): WorkoutItem {
        val original = item.replacedFromId?.let { catalog.exercise(it) } ?: catalog.exercise(item.exerciseId)
        val backToOriginal = replacement.id == item.replacedFromId
        val target = if (replacement.execution == item.execution) item.target else ProgressionEngine.defaultTarget(replacement)
        return item.copy(
            exerciseId = replacement.id,
            exerciseName = replacement.name,
            pattern = replacement.pattern,
            execution = replacement.execution,
            target = target,
            perSide = replacement.perSide,
            load = loadFor(replacement, equipment, history),
            dumbbellUse = replacement.equipment.dumbbells,
            cues = replacement.cues,
            replacedFromId = if (backToOriginal) null else original?.id,
            replacedFromName = if (backToOriginal) null else original?.name,
            change = if (backToOriginal) null else ItemChange(
                ChangeKind.HOLD,
                "Replaced ${original?.name ?: item.exerciseName} for this workout only.",
                TrainingRules.VERSION,
            ),
        )
    }

    fun easierOption(
        item: WorkoutItem,
        otherExerciseIds: Set<ExerciseId>,
        constraints: Constraints,
        equipment: EquipmentProfile,
        history: TrainingHistory,
    ): EasierOption {
        val current = catalog.exercise(item.exerciseId) ?: return EasierOption.None("This exercise is no longer in the catalog.")
        progression.easierVariation(current, constraints, otherExerciseIds)?.let { return EasierOption.Variation(it) }
        val load = item.load
        val use = item.dumbbellUse
        if (load != null && use != null) {
            EquipmentLoads.nextDown(EquipmentLoads.available(equipment, use), load)?.let {
                return EasierOption.Lighter(it)
            }
        }
        when (val t = item.target) {
            is Target.Reps -> if (t.min > TrainingRules.REP_FLOOR) {
                val min = (t.min - 2).coerceAtLeast(TrainingRules.REP_FLOOR)
                return EasierOption.SmallerTarget(Target.Reps(min, (t.max - 2).coerceAtLeast(min)))
            }
            is Target.Time -> if (t.seconds > TrainingRules.TIME_FLOOR_SECONDS) {
                return EasierOption.SmallerTarget(Target.Time((t.seconds - 10).coerceAtLeast(TrainingRules.TIME_FLOOR_SECONDS)))
            }
        }
        if (item.sets > 1) return EasierOption.FewerSets(item.sets - 1)
        return EasierOption.None("${current.name} is already at its easiest setting here. You can replace it or leave it out today.")
    }

    fun applyEasier(item: WorkoutItem, option: EasierOption, equipment: EquipmentProfile, history: TrainingHistory): WorkoutItem {
        val note = { text: String -> ItemChange(ChangeKind.HOLD, text, TrainingRules.VERSION) }
        return when (option) {
            is EasierOption.Variation -> applyReplacement(item, option.exercise, equipment, history)
                .copy(change = ItemChange(ChangeKind.EASIER_VARIATION, "Easier today: ${option.exercise.name} instead of ${item.exerciseName}.", TrainingRules.VERSION))
            is EasierOption.Lighter -> item.copy(load = option.load, change = note("Easier today: ${option.load.format()}."))
            is EasierOption.SmallerTarget -> item.copy(target = option.target, change = note("Easier today: a smaller target."))
            is EasierOption.FewerSets -> item.copy(sets = option.sets, change = note("Easier today: one fewer set."))
            is EasierOption.None -> item
        }
    }

    private fun loadFor(exercise: Exercise, equipment: EquipmentProfile, history: TrainingHistory): Load? {
        val use = exercise.equipment.dumbbells ?: return null
        val loads = EquipmentLoads.available(equipment, use)
        if (loads.isEmpty()) return null
        val last = history.forExercise(exercise.id).firstNotNullOfOrNull { it.performedLoad }
        return last?.let { EquipmentLoads.snap(loads, it) } ?: loads.first()
    }

    private fun relationTo(current: Exercise, other: Exercise): Relation = when {
        other.family == current.family && other.familyRank < current.familyRank -> Relation.EASIER
        other.family == current.family && other.familyRank > current.familyRank -> Relation.HARDER
        other.minLevel.rank < current.minLevel.rank -> Relation.EASIER
        other.minLevel.rank > current.minLevel.rank -> Relation.HARDER
        else -> Relation.SIMILAR
    }

    companion object {
        fun isTimed(exercise: Exercise) = exercise.execution == Execution.TIME
    }
}
