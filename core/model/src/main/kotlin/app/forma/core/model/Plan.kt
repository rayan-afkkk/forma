package app.forma.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Per-set target. */
@Serializable
sealed interface Target {
    @Serializable
    @SerialName("reps")
    data class Reps(val min: Int, val max: Int) : Target {
        init {
            require(min in 1..max) { "Invalid rep range $min-$max" }
        }
    }

    @Serializable
    @SerialName("time")
    data class Time(val seconds: Int) : Target {
        init {
            require(seconds > 0) { "Time target must be positive" }
        }
    }
}

/** Where a generated workout came from. */
@Serializable
sealed interface PlanSource {
    val sessionTemplateId: String

    @Serializable
    @SerialName("program")
    data class Program(
        val programId: String,
        override val sessionTemplateId: String,
        val rotationIndex: Int,
    ) : PlanSource

    @Serializable
    @SerialName("template")
    data class Template(override val sessionTemplateId: String) : PlanSource
}

@Serializable
enum class ChangeKind {
    LOAD_UP,
    LOAD_DOWN,
    MORE_REPS,
    FEWER_REPS,
    MORE_TIME,
    LESS_TIME,
    EXTRA_SET,
    FEWER_SETS,
    HARDER_VARIATION,
    EASIER_VARIATION,
    HOLD,
    FIRST_TIME,
    RETURN_EASE,
}

/** Explains a generated change to one exercise. Recorded with the rule version for testing and support. */
@Serializable
data class ItemChange(
    val kind: ChangeKind,
    val message: String,
    val ruleVersion: String,
)

/**
 * One exercise in a generated or performed workout. This is a snapshot: names, cues and targets are
 * copied from the catalog so later catalog changes never rewrite past sessions.
 */
@Serializable
data class WorkoutItem(
    val key: String,
    val slotId: String?,
    val phase: Phase,
    val exerciseId: ExerciseId,
    val exerciseName: String,
    val pattern: MovementPattern,
    val execution: Execution,
    val sets: Int,
    val target: Target,
    val perSide: Boolean,
    /**
     * Sets chosen by the program and progression rules, before one-day adjustments such as
     * shortening, an easier session or easing back after a break. Progression carries this value
     * forward so a one-off shortened session does not permanently reduce the plan.
     */
    val programmedSets: Int = sets,
    val load: Load? = null,
    val dumbbellUse: DumbbellUse? = null,
    val restSeconds: Int,
    val cues: List<String>,
    val optional: Boolean,
    /** Set when this exercise temporarily replaces another for this workout only. */
    val replacedFromId: ExerciseId? = null,
    val replacedFromName: String? = null,
    val change: ItemChange? = null,
)

@Serializable
enum class NoteKind {
    SUMMARY,
    PROGRESSION,
    RETURN,
    CONSTRAINT,
    OMITTED,
    REPLACED,
    SHORTENED,
    EASIER,
    QUIET,
    PRO_LIMIT,
    INFO,
}

/** A plain-language explanation attached to a plan. */
@Serializable
data class PlanNote(
    val kind: NoteKind,
    val text: String,
    val exerciseId: ExerciseId? = null,
)

/** Choices made in "Adjust today" or in a workout preview. They apply to one plan on one day only. */
@Serializable
data class PlanAdjustments(
    val targetMinutes: Int? = null,
    val easier: Boolean = false,
    /** Null means "use my profile setting". */
    val quiet: Boolean? = null,
    val equipmentProfileId: String? = null,
    /** Temporary replacements by item key -> exercise id. Never become permanent exclusions. */
    val replacements: Map<String, ExerciseId> = emptyMap(),
    /** Items the person chose to leave out today. */
    val omitted: Set<String> = emptySet(),
    /** Items the person asked to make easier today. */
    val easierItems: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = targetMinutes == null && !easier && quiet == null && equipmentProfileId == null &&
            replacements.isEmpty() && omitted.isEmpty() && easierItems.isEmpty()
}

@Serializable
data class WorkoutPlan(
    val id: String,
    val title: String,
    val focus: String,
    val purpose: String,
    val source: PlanSource,
    val items: List<WorkoutItem>,
    val notes: List<PlanNote>,
    val estimatedSeconds: Int,
    val equipmentProfileId: String,
    val equipmentSummary: String,
    val ruleVersion: String,
    /** True when adaptive progression rules shaped this plan. */
    val adaptive: Boolean,
    val adjustments: PlanAdjustments = PlanAdjustments(),
) {
    val totalSets: Int get() = items.sumOf { it.sets }
    val estimatedMinutes: Int get() = ((estimatedSeconds + 30) / 60).coerceAtLeast(1)
    fun item(key: String): WorkoutItem? = items.firstOrNull { it.key == key }
}
