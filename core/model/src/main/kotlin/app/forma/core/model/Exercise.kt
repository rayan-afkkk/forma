package app.forma.core.model

import kotlinx.serialization.Serializable

typealias ExerciseId = String

@Serializable
enum class MovementPattern(val label: String) {
    SQUAT("Squat"),
    LUNGE("Single-leg"),
    HINGE("Hinge"),
    PUSH_HORIZONTAL("Horizontal push"),
    PUSH_VERTICAL("Overhead push"),
    PULL("Pull and upper back"),
    CORE("Core"),
    ARMS("Arms"),
    CONDITIONING("Conditioning"),
    WARMUP("Warm-up"),
    MOBILITY("Mobility"),
    STRETCH("Stretch"),
}

@Serializable
enum class Phase(val label: String) {
    WARMUP("Warm-up"),
    MAIN("Main work"),
    COOLDOWN("Cool-down"),
}

@Serializable
enum class ExperienceLevel(val label: String, val rank: Int) {
    NEW("New to training", 0),
    SOME("Some experience", 1),
    EXPERIENCED("Experienced", 2),
}

@Serializable
enum class Execution { REPS, TIME }

@Serializable
enum class NoiseLevel { QUIET, MODERATE, LOUD }

@Serializable
enum class SpaceNeed {
    /** Room to lie down on a mat. */
    SMALL,

    /** Room to step forward or back. */
    MEDIUM,

    /** Room to travel several steps. */
    LARGE,
}

/** Content review state. Everything bundled in this version is a draft until a qualified trainer signs it off. */
@Serializable
enum class ReviewStatus { DRAFT, TRAINER_REVIEWED }

@Serializable
data class Exercise(
    val id: ExerciseId,
    val name: String,
    val pattern: MovementPattern,
    val phases: Set<Phase>,
    val equipment: EquipmentRequirement,
    val minLevel: ExperienceLevel,
    val execution: Execution,
    /** Default per-set target when a slot does not specify one. */
    val defaultRepsMin: Int = 8,
    val defaultRepsMax: Int = 12,
    val defaultSeconds: Int = 30,
    /** Reps or time are per side (e.g. split squat, side plank). */
    val perSide: Boolean = false,
    val noise: NoiseLevel = NoiseLevel.QUIET,
    val space: SpaceNeed = SpaceNeed.SMALL,
    val floor: Boolean = false,
    /** Variation family and position within it; lower rank is easier. */
    val family: String,
    val familyRank: Int,
    /** Reviewed substitution relationships, most suitable first. Draft until trainer review. */
    val substitutes: List<ExerciseId> = emptyList(),
    val summary: String,
    val cues: List<String>,
    val demoAsset: String? = null,
    val review: ReviewStatus = ReviewStatus.DRAFT,
)

/** A permanent exclusion. Survives restarts, program changes and regeneration until restored. */
@Serializable
data class ExerciseExclusion(
    val exerciseId: ExerciseId,
    val createdAt: Long,
)
