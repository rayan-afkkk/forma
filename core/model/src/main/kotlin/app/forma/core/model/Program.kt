package app.forma.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Goal(val label: String, val description: String) {
    STRENGTH("Build strength", "Gradually lift more or do harder variations."),
    GENERAL_FITNESS("Improve general fitness", "A balanced mix of strength, mobility and steady effort."),
    CONSISTENCY("Establish consistency", "Short, manageable sessions that fit into your week."),
}

@Serializable
enum class Access { FREE, PRO }

@Serializable
enum class ExploreCategory(val label: String) {
    BEGINNER("Beginner"),
    FULL_BODY("Full body"),
    DUMBBELLS("Dumbbells"),
    BODYWEIGHT("Bodyweight"),
    SHORT("Short sessions"),
    MOBILITY("Mobility"),
}

/**
 * The amount of work for a slot. Rep-based exercises use [repsMin]..[repsMax]; time-based
 * exercises use [seconds]. Values are draft programming pending trainer review.
 */
@Serializable
data class Dose(
    val sets: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val seconds: Int? = null,
    val restSeconds: Int,
    /** Upper bound the progression rules may reach by adding sets. */
    val maxSets: Int = sets,
)

/**
 * A place in a session template that the generator fills with one exercise.
 *
 * [preferred] lists exercises in the order the program author prefers them; any other exercise
 * with a matching [patterns] entry may be used if none of the preferred ones fit the person's
 * constraints. [optional] slots are the first to go when a session is shortened.
 */
@Serializable
data class Slot(
    val id: String,
    val phase: Phase,
    val patterns: Set<MovementPattern>,
    val preferred: List<ExerciseId>,
    val dose: Dose,
    val optional: Boolean = false,
    /** Human description of the slot's training role, used in explanations. */
    val role: String,
)

@Serializable
data class SessionTemplate(
    val id: String,
    val name: String,
    val focus: String,
    val purpose: String,
    val slots: List<Slot>,
    val access: Access = Access.FREE,
    val categories: Set<ExploreCategory> = emptySet(),
    val level: ExperienceLevel = ExperienceLevel.NEW,
    /** Standalone sessions appear in Explore; program sessions appear through their program. */
    val standalone: Boolean = false,
)

@Serializable
data class ProgramTemplate(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val access: Access,
    val goals: Set<Goal>,
    val level: ExperienceLevel,
    val minSessionsPerWeek: Int,
    val maxSessionsPerWeek: Int,
    /** Sessions rotate in order: A, B, A, B... or A, B, C... */
    val rotation: List<SessionTemplate>,
    val requiresDumbbells: Boolean = false,
    val categories: Set<ExploreCategory> = emptySet(),
)

@Serializable
data class ProgramEnrollment(
    val programId: String,
    val startedAt: Long,
    /** Index into the program rotation for the next session. */
    val rotationIndex: Int,
)
