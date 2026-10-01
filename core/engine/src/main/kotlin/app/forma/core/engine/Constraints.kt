package app.forma.core.engine

import app.forma.core.model.BenchNeed
import app.forma.core.model.DumbbellUse
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Exercise
import app.forma.core.model.ExerciseId
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.NoiseLevel
import app.forma.core.model.SpaceNeed

/** Why an exercise cannot be used. Every value is a hard constraint the product promises to honour. */
enum class Violation(val phrase: String) {
    EXCLUDED("you excluded it"),
    NEEDS_DUMBBELLS("it needs dumbbells"),
    NEEDS_PAIR("it needs a matching pair of dumbbells"),
    NEEDS_BENCH("it needs a bench"),
    TOO_LOUD("it involves jumping or stomping"),
    NEEDS_SPACE("it needs room to step"),
    LEVEL("it is a more advanced variation"),
}

/**
 * Hard constraints for one person on one day. Preferences such as continuity or variety are soft
 * and handled by the generator; nothing here is ever relaxed silently.
 */
data class Constraints(
    val equipment: EquipmentProfile,
    val exclusions: Set<ExerciseId>,
    val quiet: Boolean,
    val limitedSpace: Boolean,
    val level: ExperienceLevel,
) {
    fun violations(exercise: Exercise): List<Violation> {
        val result = mutableListOf<Violation>()
        if (exercise.id in exclusions) result += Violation.EXCLUDED
        exercise.equipment.dumbbells?.let { use ->
            if (EquipmentLoads.available(equipment, use).isEmpty()) {
                val hasAnyDumbbell = EquipmentLoads.available(equipment, DumbbellUse.SINGLE).isNotEmpty()
                result += if (use == DumbbellUse.PAIR && hasAnyDumbbell) Violation.NEEDS_PAIR else Violation.NEEDS_DUMBBELLS
            }
        }
        if (exercise.equipment.bench == BenchNeed.REQUIRED && !equipment.hasBench) result += Violation.NEEDS_BENCH
        if (quiet && exercise.noise == NoiseLevel.LOUD) result += Violation.TOO_LOUD
        if (limitedSpace && exercise.space != SpaceNeed.SMALL) result += Violation.NEEDS_SPACE
        if (exercise.minLevel.rank > level.rank) result += Violation.LEVEL
        return result
    }

    fun allows(exercise: Exercise): Boolean = violations(exercise).isEmpty()
}

/** Builds a plain-language explanation from the reasons candidates were rejected. */
internal fun explainRejections(role: String, rejected: Map<Exercise, List<Violation>>): String {
    val reasons = rejected.values.flatten().toSet()
    val phrases = Violation.entries.filter { it in reasons }.map { it.phrase }
    val joined = when (phrases.size) {
        0 -> "none of the options fit today"
        1 -> "the options don't fit: ${phrases[0]}"
        else -> "each option is ruled out because " +
            phrases.dropLast(1).joinToString(", ") + " or " + phrases.last()
    }
    return "No ${role.lowercase()} exercise fits your current setup — $joined."
}
