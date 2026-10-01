package app.forma.core.engine

import app.forma.core.model.ChangeKind
import app.forma.core.model.DumbbellUse
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Execution
import app.forma.core.model.Exercise
import app.forma.core.model.ExerciseId
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.ItemChange
import app.forma.core.model.Load
import app.forma.core.model.NoteKind
import app.forma.core.model.Phase
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.PlanNote
import app.forma.core.model.PlanSource
import app.forma.core.model.SessionTemplate
import app.forma.core.model.Slot
import app.forma.core.model.Target
import app.forma.core.model.UserProfile
import app.forma.core.model.WorkoutItem
import app.forma.core.model.WorkoutPlan
import app.forma.core.model.format

data class GenerationInput(
    val template: SessionTemplate,
    val source: PlanSource,
    val planId: String,
    val profile: UserProfile,
    val equipment: EquipmentProfile,
    val exclusions: Set<ExerciseId>,
    val history: TrainingHistory,
    /** Automatic increases allowed (Pro, or within the free adaptive sample). */
    val adaptive: Boolean,
    /** Easing back after a break. */
    val returnEase: Boolean = false,
    val adjustments: PlanAdjustments = PlanAdjustments(),
)

data class GeneratedPlan(
    val plan: WorkoutPlan,
    /** Set when the requested duration could not be met. The plan is the shortest practical version. */
    val impractical: FitResult.Impractical? = null,
)

class WorkoutGenerator(
    private val catalog: ContentCatalog = ContentCatalog.Default,
) {
    private val progression = ProgressionEngine(catalog)
    val substitutions = Substitutions(catalog, progression)

    fun constraintsFor(profile: UserProfile, equipment: EquipmentProfile, exclusions: Set<ExerciseId>, quiet: Boolean? = null) =
        Constraints(
            equipment = equipment,
            exclusions = exclusions,
            quiet = quiet ?: profile.quiet,
            limitedSpace = profile.limitedSpace,
            level = profile.experience,
        )

    fun generate(input: GenerationInput): GeneratedPlan {
        val adjustments = input.adjustments
        val constraints = constraintsFor(input.profile, input.equipment, input.exclusions, adjustments.quiet)
        val notes = mutableListOf<PlanNote>()
        val used = mutableSetOf<ExerciseId>()
        var items = mutableListOf<WorkoutItem>()
        var quietFiltered = false

        for (slot in input.template.slots) {
            val fill = fillSlot(slot, input.template, used, constraints, input.history)
            if (fill.rejected.values.any { Violation.TOO_LOUD in it }) quietFiltered = true
            val chosen: Exercise = fill.exercise ?: run {
                val suffix = if (slot.optional) " This optional part is left out today."
                else " This session continues without it. You can review your equipment and exclusions in You."
                notes += PlanNote(NoteKind.CONSTRAINT, explainRejections(slot.role, fill.rejected) + suffix)
                null
            } ?: continue

            // A temporary replacement chosen for this plan only.
            var exercise: Exercise = chosen
            var replacedFrom: Exercise? = null
            adjustments.replacements[slot.id]?.let { replacementId ->
                val replacement = catalog.exercise(replacementId)
                if (replacement != null && constraints.allows(replacement) && replacement.id !in used) {
                    replacedFrom = chosen
                    exercise = replacement
                } else {
                    notes += PlanNote(
                        NoteKind.INFO,
                        "Your replacement for ${chosen.name} doesn't fit today's setup, so ${chosen.name} is back.",
                        chosen.id,
                    )
                }
            }
            used += exercise.id

            var item = buildItem(slot, exercise, input, constraints, used, replacedFrom)
            if (item.exerciseId != exercise.id) {
                // Progression switched to a different variation.
                used -= exercise.id
                used += item.exerciseId
            }
            if (slot.id in adjustments.easierItems) {
                item = makeEasier(item, constraints, used, input) ?: item
            }
            items += item
        }

        // Returning after a break: one fewer set and one step lighter on main work, for this session only.
        if (input.returnEase) {
            items = items.map { item ->
                if (item.phase != Phase.MAIN) return@map item
                val lighter = item.load?.let { load ->
                    item.dumbbellUse?.let { EquipmentLoads.nextDown(EquipmentLoads.available(input.equipment, it), load) }
                }
                item.copy(sets = (item.sets - 1).coerceAtLeast(1), load = lighter ?: item.load)
            }.toMutableList()
            notes += PlanNote(
                NoteKind.RETURN,
                "Welcome back. To ease in after your break, main exercises have one fewer set and slightly lighter weights where possible.",
            )
        }

        if (adjustments.easier) {
            items = items.map { item ->
                if (item.phase != Phase.MAIN) item
                else item.copy(sets = (item.sets - 1).coerceAtLeast(1), restSeconds = item.restSeconds + 15)
            }.toMutableList()
            notes += PlanNote(NoteKind.EASIER, "Easier session: one fewer set on main exercises and a little more rest.")
        }

        if (adjustments.omitted.isNotEmpty()) {
            val omitted = items.filter { it.key in adjustments.omitted }
            items = items.filterNot { it.key in adjustments.omitted }.toMutableList()
            for (item in omitted) {
                notes += PlanNote(NoteKind.OMITTED, "You left out ${item.exerciseName} today.", item.exerciseId)
            }
        }

        if (quietFiltered) {
            notes += PlanNote(NoteKind.QUIET, "Quiet workout: exercises with jumping or stomping are left out.")
        }

        var impractical: FitResult.Impractical? = null
        adjustments.targetMinutes?.let { minutes ->
            when (val fit = DurationFitter.fit(items, minutes)) {
                is FitResult.Fits -> {
                    items = fit.items.toMutableList()
                    notes += fit.notes
                }
                is FitResult.Impractical -> {
                    items = fit.items.toMutableList()
                    notes += fit.notes
                    impractical = fit
                }
            }
        }

        val summary = summaryNotes(items, input)
        val plan = WorkoutPlan(
            id = input.planId,
            title = input.template.name,
            focus = input.template.focus,
            purpose = input.template.purpose,
            source = input.source,
            items = items,
            notes = summary + notes,
            estimatedSeconds = DurationEstimator.totalSeconds(items),
            equipmentProfileId = input.equipment.id,
            equipmentSummary = equipmentSummary(items, catalog),
            ruleVersion = TrainingRules.VERSION,
            adaptive = input.adaptive,
            adjustments = adjustments,
        )
        return GeneratedPlan(plan, impractical)
    }

    // ------------------------------------------------------------------------------------------ slots

    internal data class SlotFill(val exercise: Exercise?, val rejected: Map<Exercise, List<Violation>>)

    internal fun fillSlot(
        slot: Slot,
        template: SessionTemplate,
        used: Set<ExerciseId>,
        constraints: Constraints,
        history: TrainingHistory,
    ): SlotFill {
        val preferred = slot.preferred.mapNotNull { catalog.exercise(it) }.filter { slot.phase in it.phases }
        val others = catalog.exercises
            .filter { it.pattern in slot.patterns && slot.phase in it.phases && it !in preferred }
            .sortedWith(compareBy<Exercise>({ constraints.level.rank - it.minLevel.rank }, { it.familyRank }))
        val rejected = linkedMapOf<Exercise, List<Violation>>()
        val allowed = (preferred + others).filter { exercise ->
            if (exercise.id in used) return@filter false
            val violations = constraints.violations(exercise)
            if (violations.isNotEmpty()) rejected[exercise] = violations
            violations.isEmpty()
        }
        // Continuity: keep the exercise this slot used last time, if it still fits.
        // Temporary replacements and one-day easier swaps do not change what the slot uses next time.
        val last = history.lastForSlot(template.id, slot.id)?.item?.let { it.replacedFromId ?: it.exerciseId }
        val chosen = allowed.firstOrNull { it.id == last } ?: allowed.firstOrNull()
        return SlotFill(chosen, rejected)
    }

    private fun buildItem(
        slot: Slot,
        exercise: Exercise,
        input: GenerationInput,
        constraints: Constraints,
        used: Set<ExerciseId>,
        replacedFrom: Exercise?,
    ): WorkoutItem {
        val base = Prescription(
            exercise = exercise,
            sets = setsFor(slot, input.profile),
            maxSets = slot.dose.maxSets.coerceAtLeast(setsFor(slot, input.profile)),
            target = targetFor(exercise, slot, input.profile),
            load = startLoad(exercise, input.equipment, input.history),
        )
        val result = if (slot.phase == Phase.MAIN && replacedFrom == null) {
            progression.apply(
                base = base,
                exposures = input.history.forExercise(exercise.id),
                loads = loadsFor(exercise, input.equipment),
                constraints = constraints,
                usedElsewhere = used - exercise.id,
                allowIncrease = input.adaptive,
            )
        } else {
            ProgressionResult(base, null)
        }
        var p = result.prescription
        if (p.exercise.id != exercise.id) {
            p = p.copy(load = startLoad(p.exercise, input.equipment, input.history))
        }
        if (p.exercise.equipment.dumbbells != null && p.load == null) {
            p = p.copy(load = startLoad(p.exercise, input.equipment, input.history))
        }
        // On someone's very first session the summary note already says "start light"; per-item
        // first-time notes would only repeat it.
        val firstTimeNoise = input.history.isEmpty && result.change?.kind == ChangeKind.FIRST_TIME
        val change = result.change?.takeUnless { firstTimeNoise } ?: replacedFrom?.let {
            ItemChange(ChangeKind.HOLD, "Replaced ${it.name} for this workout only.", TrainingRules.VERSION)
        }
        return toItem(slot, p, change, replacedFrom)
    }

    private fun toItem(slot: Slot, p: Prescription, change: ItemChange?, replacedFrom: Exercise?) = WorkoutItem(
        key = slot.id,
        slotId = slot.id,
        phase = slot.phase,
        exerciseId = p.exercise.id,
        exerciseName = p.exercise.name,
        pattern = p.exercise.pattern,
        execution = p.exercise.execution,
        sets = p.sets,
        target = p.target,
        perSide = p.exercise.perSide,
        programmedSets = p.sets,
        load = if (p.exercise.equipment.dumbbells != null) p.load else null,
        dumbbellUse = p.exercise.equipment.dumbbells,
        restSeconds = slot.dose.restSeconds,
        cues = p.exercise.cues,
        optional = slot.optional,
        replacedFromId = replacedFrom?.id,
        replacedFromName = replacedFrom?.name,
        change = change,
    )

    private fun setsFor(slot: Slot, profile: UserProfile): Int {
        if (slot.phase != Phase.MAIN) return slot.dose.sets
        val reduce = profile.experience == ExperienceLevel.NEW || profile.goal == Goal.CONSISTENCY
        return if (reduce && slot.dose.sets > 2) slot.dose.sets - 1 else slot.dose.sets
    }

    internal fun targetFor(exercise: Exercise, slot: Slot, profile: UserProfile): Target = when (exercise.execution) {
        Execution.TIME -> Target.Time(slot.dose.seconds ?: exercise.defaultSeconds)
        Execution.REPS -> {
            val min = slot.dose.repsMin ?: exercise.defaultRepsMin
            val max = slot.dose.repsMax ?: exercise.defaultRepsMax
            val loaded = exercise.equipment.dumbbells != null
            if (slot.phase == Phase.MAIN && loaded && profile.goal == Goal.STRENGTH && profile.experience != ExperienceLevel.NEW) {
                Target.Reps((min - 2).coerceAtLeast(5), (max - 2).coerceAtLeast(6))
            } else {
                Target.Reps(min, max.coerceAtLeast(min))
            }
        }
    }

    internal fun loadsFor(exercise: Exercise, equipment: EquipmentProfile): List<Load> =
        exercise.equipment.dumbbells?.let { EquipmentLoads.available(equipment, it) } ?: emptyList()

    /** Last performed load (snapped to what is available now), else the lightest available. */
    internal fun startLoad(exercise: Exercise, equipment: EquipmentProfile, history: TrainingHistory): Load? {
        val loads = loadsFor(exercise, equipment)
        if (loads.isEmpty()) return null
        val last = history.forExercise(exercise.id).firstNotNullOfOrNull { it.performedLoad }
        return last?.let { EquipmentLoads.snap(loads, it) } ?: loads.first()
    }

    private fun makeEasier(item: WorkoutItem, constraints: Constraints, used: Set<ExerciseId>, input: GenerationInput): WorkoutItem? =
        when (val option = substitutions.easierOption(item, used - item.exerciseId, constraints, input.equipment, input.history)) {
            is EasierOption.None -> null
            else -> substitutions.applyEasier(item, option, input.equipment, input.history)
        }

    // ------------------------------------------------------------------------------------------ notes

    private fun summaryNotes(items: List<WorkoutItem>, input: GenerationInput): List<PlanNote> {
        val kinds = items.mapNotNull { it.change?.kind }.toSet()
        val increases = kinds.intersect(
            setOf(ChangeKind.LOAD_UP, ChangeKind.MORE_REPS, ChangeKind.MORE_TIME, ChangeKind.EXTRA_SET, ChangeKind.HARDER_VARIATION),
        )
        val decreases = kinds.intersect(
            setOf(ChangeKind.LOAD_DOWN, ChangeKind.FEWER_REPS, ChangeKind.LESS_TIME, ChangeKind.FEWER_SETS, ChangeKind.EASIER_VARIATION),
        )
        val mains = items.filter { it.phase == Phase.MAIN }
        val allFirstTime = mains.isNotEmpty() && mains.all { it.change?.kind == ChangeKind.FIRST_TIME }
        val text = when {
            input.history.isEmpty && items.any { it.load != null } ->
                "Your first session. Start light and adjust weights and reps as you go. What you log shapes your next session."
            input.history.isEmpty -> "Your first session. Move at a comfortable pace and adjust reps as you go. What you log shapes your next session."
            allFirstTime -> "A new session for you. Start light and adjust as you go."
            increases.isNotEmpty() && decreases.isNotEmpty() ->
                "Based on your recent sessions, a few exercises progress slightly today and a few ease off."
            increases.isNotEmpty() -> "You completed your previous sessions comfortably, so today's plan includes a small progression."
            decreases.isNotEmpty() -> "Some recent sets felt too hard, so a few exercises are slightly easier today."
            else -> "Today repeats your recent targets. Practising the same session is how progress builds."
        }
        val notes = mutableListOf(PlanNote(NoteKind.SUMMARY, text))
        if (!input.adaptive && !input.history.isEmpty) {
            notes += PlanNote(
                NoteKind.PRO_LIMIT,
                "Automatic progression is part of Pro. Today keeps your last weights and targets; you can change them during the workout.",
            )
        }
        return notes
    }

    companion object {
        fun equipmentSummary(items: List<WorkoutItem>, catalog: ContentCatalog): String {
            val uses = items.mapNotNull { it.dumbbellUse }.toSet()
            val bench = items.any { catalog.exercise(it.exerciseId)?.equipment?.bench == app.forma.core.model.BenchNeed.REQUIRED }
            val parts = mutableListOf<String>()
            when {
                DumbbellUse.PAIR in uses -> parts += "Dumbbells"
                uses.isNotEmpty() -> parts += "1 dumbbell"
            }
            if (bench) parts += "Bench"
            return if (parts.isEmpty()) "No equipment" else parts.joinToString(" · ")
        }

        fun describeLoad(load: Load?): String? = load?.format()
    }
}
