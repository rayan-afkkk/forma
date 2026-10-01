package app.forma.core.engine

import app.forma.core.model.Difficulty
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExerciseId
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Goal
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.PlanSource
import app.forma.core.model.SessionFeedback
import app.forma.core.model.SessionStatus
import app.forma.core.model.SetLog
import app.forma.core.model.Target
import app.forma.core.model.UserProfile
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutPlan
import app.forma.core.model.WorkoutSession

object Fixtures {
    val catalog = ContentCatalog.Default
    val generator = WorkoutGenerator(catalog)

    val bodyweight = EquipmentProfile("bw", "Bodyweight", WeightUnit.KG, emptyList())
    val benchOnly = EquipmentProfile("bench", "Bench", WeightUnit.KG, listOf(EquipmentItem.Bench))
    val singleDumbbell = EquipmentProfile(
        "single", "One dumbbell", WeightUnit.KG,
        listOf(EquipmentItem.FixedDumbbells(800, 1)),
    )
    val fixedPairs = EquipmentProfile(
        "fixed", "Fixed pairs", WeightUnit.KG,
        listOf(
            EquipmentItem.FixedDumbbells(500, 2),
            EquipmentItem.FixedDumbbells(1000, 2),
            EquipmentItem.FixedDumbbells(1250, 1),
        ),
    )
    val adjustableKg = EquipmentProfile(
        "adj", "Adjustable", WeightUnit.KG,
        listOf(EquipmentItem.AdjustableDumbbells(250, 2000, 250, 2), EquipmentItem.Bench),
    )
    val adjustableLb = EquipmentProfile(
        "adjlb", "Adjustable lb", WeightUnit.LB,
        listOf(EquipmentItem.AdjustableDumbbells(500, 5000, 500, 2)),
    )
    val allProfiles = listOf(bodyweight, benchOnly, singleDumbbell, fixedPairs, adjustableKg, adjustableLb)

    fun profile(
        level: ExperienceLevel = ExperienceLevel.SOME,
        goal: Goal = Goal.GENERAL_FITNESS,
        quiet: Boolean = false,
        limitedSpace: Boolean = false,
    ) = UserProfile(name = "Test", goal = goal, experience = level, quiet = quiet, limitedSpace = limitedSpace)

    val allTemplates = catalog.programs.flatMap { it.rotation } + catalog.standaloneSessions

    fun generate(
        templateId: String = "foundations_a",
        equipment: EquipmentProfile = adjustableKg,
        profile: UserProfile = profile(),
        exclusions: Set<ExerciseId> = emptySet(),
        history: TrainingHistory = TrainingHistory.Empty,
        adaptive: Boolean = true,
        returnEase: Boolean = false,
        adjustments: PlanAdjustments = PlanAdjustments(),
    ): GeneratedPlan {
        val template = catalog.sessionTemplate(templateId)!!
        val program = catalog.programFor(templateId)
        val source = if (program != null) PlanSource.Program(program.id, templateId, 0) else PlanSource.Template(templateId)
        return generator.generate(
            GenerationInput(
                template = template,
                source = source,
                planId = "plan-$templateId",
                profile = profile,
                equipment = equipment,
                exclusions = exclusions,
                history = history,
                adaptive = adaptive,
                returnEase = returnEase,
                adjustments = adjustments,
            ),
        )
    }

    /**
     * Simulates performing [plan]: every set logged at the planned load. [repsFor] decides the reps
     * (or seconds) achieved per item; default is the top of the target.
     */
    fun perform(
        plan: WorkoutPlan,
        id: String,
        day: Int,
        difficulty: Difficulty? = Difficulty.JUST_RIGHT,
        repsFor: (app.forma.core.model.WorkoutItem) -> Int? = { item ->
            when (val t = item.target) {
                is Target.Reps -> t.max
                is Target.Time -> t.seconds
            }
        },
        loadOverride: (app.forma.core.model.WorkoutItem) -> app.forma.core.model.Load? = { it.load },
        setsDone: (app.forma.core.model.WorkoutItem) -> Int = { it.sets },
    ): WorkoutSession {
        val endedAt = day * 86_400_000L + 3_600_000L
        val sets = plan.items.flatMap { item ->
            val value = repsFor(item)
            (1..setsDone(item)).map { n ->
                SetLog(
                    sessionId = id, itemKey = item.key, setNumber = n,
                    exerciseId = item.exerciseId, exerciseName = item.exerciseName,
                    reps = if (item.target is Target.Reps) value else null,
                    seconds = if (item.target is Target.Time) value else null,
                    load = loadOverride(item),
                    completedAt = endedAt,
                )
            }
        }
        val allDone = plan.items.all { setsDone(it) >= it.sets }
        return WorkoutSession(
            id = id,
            plan = plan,
            status = if (allDone) SessionStatus.COMPLETED else SessionStatus.PARTIAL,
            startedAt = endedAt - 1_800_000L,
            endedAt = endedAt,
            zoneId = "UTC",
            localDate = java.time.LocalDate.ofEpochDay(day.toLong()).toString(),
            sets = sets,
            feedback = difficulty?.let { SessionFeedback(it, "", endedAt) },
        )
    }
}
