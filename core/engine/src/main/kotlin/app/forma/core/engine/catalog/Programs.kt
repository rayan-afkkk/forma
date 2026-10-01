package app.forma.core.engine.catalog

import app.forma.core.model.Access
import app.forma.core.model.Dose
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.ExploreCategory
import app.forma.core.model.Goal
import app.forma.core.model.MovementPattern
import app.forma.core.model.Phase
import app.forma.core.model.ProgramTemplate
import app.forma.core.model.SessionTemplate
import app.forma.core.model.Slot

/*
 * DRAFT PROGRAMMING. Set counts, rep ranges, rest periods and session structures are placeholders
 * for product development and require review by a qualified fitness professional.
 */

private fun warm(id: String, vararg preferred: String, seconds: Int = 40, reps: Int? = null, optional: Boolean = false) = Slot(
    id = id,
    phase = Phase.WARMUP,
    patterns = setOf(MovementPattern.WARMUP, MovementPattern.MOBILITY),
    preferred = preferred.toList(),
    dose = Dose(sets = 1, repsMin = reps, repsMax = reps, seconds = seconds, restSeconds = 10),
    optional = optional,
    role = "Warm-up",
)

private fun cool(id: String, vararg preferred: String, seconds: Int = 30, optional: Boolean = false) = Slot(
    id = id,
    phase = Phase.COOLDOWN,
    patterns = setOf(MovementPattern.STRETCH, MovementPattern.MOBILITY),
    preferred = preferred.toList(),
    dose = Dose(sets = 1, repsMin = 6, repsMax = 6, seconds = seconds, restSeconds = 10),
    optional = optional,
    role = "Cool-down",
)

private fun main(
    id: String,
    role: String,
    patterns: Set<MovementPattern>,
    vararg preferred: String,
    sets: Int = 3,
    reps: IntRange = 8..12,
    seconds: Int = 30,
    rest: Int = 75,
    maxSets: Int = sets + 1,
    optional: Boolean = false,
) = Slot(
    id = id,
    phase = Phase.MAIN,
    patterns = patterns,
    preferred = preferred.toList(),
    dose = Dose(sets = sets, repsMin = reps.first, repsMax = reps.last, seconds = seconds, restSeconds = rest, maxSets = maxSets),
    optional = optional,
    role = role,
)

private val SQUAT = setOf(MovementPattern.SQUAT)
private val LUNGE = setOf(MovementPattern.LUNGE)
private val HINGE = setOf(MovementPattern.HINGE)
private val PUSH = setOf(MovementPattern.PUSH_HORIZONTAL)
private val PRESS = setOf(MovementPattern.PUSH_VERTICAL, MovementPattern.PUSH_HORIZONTAL)
private val PULL = setOf(MovementPattern.PULL)
private val CORE = setOf(MovementPattern.CORE)
private val ARMS = setOf(MovementPattern.ARMS)
private val CONDITIONING = setOf(MovementPattern.CONDITIONING, MovementPattern.WARMUP)
private val MOBILITY = setOf(MovementPattern.MOBILITY, MovementPattern.STRETCH)

// ------------------------------------------------------------------------------------- Foundations (free)

private val FoundationsA = SessionTemplate(
    id = "foundations_a",
    name = "Full body A",
    focus = "Full body",
    purpose = "Squat, push, hinge, pull and core: the basic movement patterns, practised steadily.",
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
        warm("warm_2", "arm_circles", seconds = 30, optional = true),
        warm("warm_3", "hip_hinge_drill", "lunge_with_reach", reps = 8),
        main("squat", "Squat", SQUAT, "goblet_squat", "bodyweight_squat", "box_squat", "wall_sit"),
        main("push", "Push", PUSH, "db_floor_press", "push_up", "knee_push_up", "incline_push_up", "wall_push_up"),
        main("hinge", "Hinge", HINGE, "db_romanian_deadlift", "glute_bridge", "single_leg_glute_bridge"),
        main("pull", "Pull", PULL, "db_one_arm_row", "db_bent_over_row", "prone_y_raise", rest = 60),
        main("core", "Core", CORE, "dead_bug", "forearm_plank", "knee_plank", "bird_dog", sets = 2, reps = 6..10, rest = 45, optional = true),
        cool("cool_1", "hip_flexor_stretch", "figure_four_stretch"),
        cool("cool_2", "childs_pose", "standing_hamstring_stretch", seconds = 40, optional = true),
    ),
    categories = setOf(ExploreCategory.FULL_BODY, ExploreCategory.BEGINNER),
)

private val FoundationsB = SessionTemplate(
    id = "foundations_b",
    name = "Full body B",
    focus = "Full body",
    purpose = "Single-leg strength, pressing, bridging and trunk stability.",
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
        warm("warm_2", "lunge_with_reach", "hip_hinge_drill", reps = 5),
        warm("warm_3", "cat_cow", "arm_circles", reps = 8, optional = true),
        main("lunge", "Single-leg", LUNGE, "db_split_squat", "split_squat", "supported_split_squat", "reverse_lunge", reps = 8..10),
        main("press", "Press", PRESS, "db_shoulder_press", "pike_push_up", "incline_push_up", "knee_push_up", "push_up", "wall_push_up"),
        main("bridge", "Hinge", HINGE, "single_leg_glute_bridge", "glute_bridge", "db_glute_bridge", "single_leg_hip_hinge"),
        main("row", "Pull", PULL, "db_bent_over_row", "db_reverse_fly", "db_one_arm_row", "prone_y_raise", rest = 60),
        main("core", "Core", CORE, "side_plank_knees", "bird_dog", "suitcase_hold", "dead_bug", sets = 2, reps = 6..10, seconds = 20, rest = 45, optional = true),
        cool("cool_1", "standing_hamstring_stretch", "figure_four_stretch"),
        cool("cool_2", "open_book", "childs_pose", optional = true),
    ),
    categories = setOf(ExploreCategory.FULL_BODY, ExploreCategory.BEGINNER),
)

val Foundations = ProgramTemplate(
    id = "foundations",
    name = "Foundations",
    summary = "Two alternating full-body sessions for bodyweight or dumbbells.",
    description = "A complete starter program. Each session covers the main movement patterns, and the " +
        "exercises adapt to your equipment, experience and exclusions.",
    access = Access.FREE,
    goals = Goal.entries.toSet(),
    level = ExperienceLevel.NEW,
    minSessionsPerWeek = 2,
    maxSessionsPerWeek = 4,
    rotation = listOf(FoundationsA, FoundationsB),
    categories = setOf(ExploreCategory.FULL_BODY, ExploreCategory.BEGINNER),
)

// ------------------------------------------------------------------------------------- Dumbbell Strength (Pro)

private val StrengthA = SessionTemplate(
    id = "strength_a",
    name = "Squat and press",
    focus = "Strength",
    purpose = "Heavier squatting and horizontal pressing, with rowing to balance.",
    access = Access.PRO,
    level = ExperienceLevel.SOME,
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
        warm("warm_2", "hip_hinge_drill", reps = 8),
        warm("warm_3", "arm_circles", seconds = 30, optional = true),
        main("squat", "Squat", SQUAT, "goblet_squat", "tempo_squat", "bodyweight_squat", sets = 4, reps = 6..10, rest = 90, maxSets = 5),
        main("press", "Push", PUSH, "db_bench_press", "db_floor_press", "push_up", sets = 4, reps = 6..10, rest = 90, maxSets = 5),
        main("row", "Pull", PULL, "db_one_arm_row", "db_bent_over_row", sets = 3, reps = 8..12, rest = 75),
        main("carry", "Core", CORE, "suitcase_hold", "side_plank_knees", sets = 2, seconds = 30, rest = 45, optional = true),
        main("arms", "Arms", ARMS, "db_curl", sets = 2, reps = 10..15, rest = 45, optional = true),
        cool("cool_1", "hip_flexor_stretch", "figure_four_stretch"),
    ),
    categories = setOf(ExploreCategory.DUMBBELLS),
)

private val StrengthB = SessionTemplate(
    id = "strength_b",
    name = "Hinge and pull",
    focus = "Strength",
    purpose = "Hip hinging and rowing, with single-leg and overhead work.",
    access = Access.PRO,
    level = ExperienceLevel.SOME,
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
        warm("warm_2", "hip_hinge_drill", reps = 8),
        warm("warm_3", "cat_cow", reps = 8, optional = true),
        main("hinge", "Hinge", HINGE, "db_romanian_deadlift", "db_glute_bridge", sets = 4, reps = 6..10, rest = 90, maxSets = 5),
        main("row", "Pull", PULL, "db_bent_over_row", "db_one_arm_row", sets = 4, reps = 8..10, rest = 75, maxSets = 5),
        main("lunge", "Single-leg", LUNGE, "db_split_squat", "split_squat", sets = 3, reps = 8..10, rest = 75),
        main("press", "Overhead", PRESS, "db_shoulder_press", "pike_push_up", sets = 3, reps = 8..10, rest = 75),
        main("core", "Core", CORE, "dead_bug", "bird_dog", sets = 2, reps = 6..10, rest = 45, optional = true),
        cool("cool_1", "standing_hamstring_stretch", "childs_pose"),
    ),
    categories = setOf(ExploreCategory.DUMBBELLS),
)

private val StrengthC = SessionTemplate(
    id = "strength_c",
    name = "Single-leg and overhead",
    focus = "Strength",
    purpose = "Single-leg strength, overhead pressing and upper-back work.",
    access = Access.PRO,
    level = ExperienceLevel.SOME,
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
        warm("warm_2", "lunge_with_reach", reps = 5),
        warm("warm_3", "arm_circles", seconds = 30, optional = true),
        main("lunge", "Single-leg", LUNGE, "db_split_squat", "reverse_lunge", "split_squat", sets = 3, reps = 8..10, rest = 75),
        main("press", "Overhead", PRESS, "db_shoulder_press", "pike_push_up", sets = 4, reps = 6..10, rest = 90, maxSets = 5),
        main("bridge", "Hinge", HINGE, "single_leg_glute_bridge", "db_glute_bridge", sets = 3, reps = 8..12, rest = 60),
        main("fly", "Upper back", PULL, "db_reverse_fly", "prone_y_raise", sets = 3, reps = 10..15, rest = 60),
        main("push", "Push", PUSH, "push_up", "knee_push_up", sets = 2, reps = 6..12, rest = 60, optional = true),
        cool("cool_1", "figure_four_stretch", "hip_flexor_stretch"),
    ),
    categories = setOf(ExploreCategory.DUMBBELLS),
)

val DumbbellStrength = ProgramTemplate(
    id = "dumbbell_strength",
    name = "Dumbbell Strength",
    summary = "Three rotating sessions focused on gradually heavier dumbbell work.",
    description = "For people with some training experience and dumbbells at home. Sessions rotate between " +
        "squatting, hinging and single-leg work, with progression based on your logged sets.",
    access = Access.PRO,
    goals = setOf(Goal.STRENGTH),
    level = ExperienceLevel.SOME,
    minSessionsPerWeek = 2,
    maxSessionsPerWeek = 4,
    rotation = listOf(StrengthA, StrengthB, StrengthC),
    requiresDumbbells = true,
    categories = setOf(ExploreCategory.DUMBBELLS, ExploreCategory.FULL_BODY),
)

// ------------------------------------------------------------------------------------- Steady Habit (Pro)

private val HabitStrength = SessionTemplate(
    id = "habit_strength",
    name = "Steady strength",
    focus = "Full body",
    purpose = "A short full-body circuit you can fit into a busy day.",
    access = Access.PRO,
    slots = listOf(
        warm("warm_1", "march_in_place", "step_jacks", seconds = 40),
        main("squat", "Squat", SQUAT, "goblet_squat", "bodyweight_squat", "wall_sit", sets = 2, rest = 45),
        main("push", "Push", PUSH, "knee_push_up", "push_up", "incline_push_up", "wall_push_up", "db_floor_press", sets = 2, rest = 45),
        main("hinge", "Hinge", HINGE, "glute_bridge", "db_romanian_deadlift", sets = 2, rest = 45),
        main("pull", "Pull", PULL, "db_one_arm_row", "prone_y_raise", sets = 2, rest = 45),
        main("core", "Core", CORE, "dead_bug", "knee_plank", sets = 2, reps = 6..10, rest = 30, optional = true),
        cool("cool_1", "childs_pose", "standing_hamstring_stretch", seconds = 40),
    ),
    categories = setOf(ExploreCategory.FULL_BODY, ExploreCategory.SHORT),
)

private val HabitMobility = SessionTemplate(
    id = "habit_mobility",
    name = "Mobility flow",
    focus = "Mobility",
    purpose = "Easy movement for the hips, back and shoulders.",
    access = Access.PRO,
    slots = listOf(
        warm("warm_1", "march_in_place", seconds = 40),
        main("flow_1", "Spine", MOBILITY, "cat_cow", sets = 2, reps = 8..10, rest = 15),
        main("flow_2", "Hips", MOBILITY, "lunge_with_reach", sets = 2, reps = 4..6, rest = 15),
        main("flow_3", "Upper back", MOBILITY, "open_book", sets = 2, reps = 5..6, rest = 15),
        main("flow_4", "Trunk", CORE, "bird_dog", sets = 2, reps = 6..8, rest = 20),
        cool("cool_1", "hip_flexor_stretch"),
        cool("cool_2", "figure_four_stretch"),
        cool("cool_3", "childs_pose", seconds = 45, optional = true),
    ),
    categories = setOf(ExploreCategory.MOBILITY),
)

private val HabitConditioning = SessionTemplate(
    id = "habit_conditioning",
    name = "Steady conditioning",
    focus = "Conditioning",
    purpose = "Raise your heart rate with simple, low-impact intervals.",
    access = Access.PRO,
    slots = listOf(
        warm("warm_1", "march_in_place", seconds = 60),
        main("interval_1", "Conditioning", CONDITIONING, "shadow_boxing", "step_jacks", sets = 3, seconds = 40, rest = 20),
        main("interval_2", "Legs", SQUAT, "bodyweight_squat", "wall_sit", sets = 2, reps = 10..15, rest = 30),
        main("interval_3", "Conditioning", CONDITIONING, "mountain_climber", "step_jacks", "march_in_place", sets = 3, seconds = 30, rest = 30),
        main("interval_4", "Core", CORE, "dead_bug", "bird_dog", sets = 2, reps = 6..10, rest = 30, optional = true),
        cool("cool_1", "standing_hamstring_stretch", "childs_pose"),
    ),
    categories = setOf(ExploreCategory.BODYWEIGHT, ExploreCategory.SHORT),
)

val SteadyHabit = ProgramTemplate(
    id = "steady_habit",
    name = "Steady Habit",
    summary = "Short strength, mobility and conditioning sessions that rotate through the week.",
    description = "Built for consistency. Each session takes about 20 minutes and rotates between strength, " +
        "mobility and easy conditioning so you can keep showing up.",
    access = Access.PRO,
    goals = setOf(Goal.CONSISTENCY, Goal.GENERAL_FITNESS),
    level = ExperienceLevel.NEW,
    minSessionsPerWeek = 3,
    maxSessionsPerWeek = 5,
    rotation = listOf(HabitStrength, HabitMobility, HabitConditioning),
    categories = setOf(ExploreCategory.SHORT, ExploreCategory.MOBILITY, ExploreCategory.BODYWEIGHT),
)

// ------------------------------------------------------------------------------------- Standalone sessions

val StandaloneSessions: List<SessionTemplate> = listOf(
    SessionTemplate(
        id = "first_steps",
        name = "First steps",
        focus = "Full body",
        purpose = "A gentle introduction to squatting, pushing, bridging and trunk control.",
        standalone = true,
        categories = setOf(ExploreCategory.BEGINNER, ExploreCategory.BODYWEIGHT, ExploreCategory.FULL_BODY),
        slots = listOf(
            warm("warm_1", "march_in_place", seconds = 45),
            main("squat", "Squat", SQUAT, "box_squat", "bodyweight_squat", "wall_sit", sets = 2, reps = 8..10, rest = 60),
            main("push", "Push", PUSH, "wall_push_up", "incline_push_up", sets = 2, reps = 8..12, rest = 60),
            main("bridge", "Hinge", HINGE, "glute_bridge", sets = 2, reps = 8..12, rest = 60),
            main("core", "Core", CORE, "bird_dog", "dead_bug", sets = 2, reps = 6..8, rest = 45),
            cool("cool_1", "standing_hamstring_stretch", "childs_pose"),
        ),
    ),
    SessionTemplate(
        id = "quick_full_body",
        name = "15-minute full body",
        focus = "Full body",
        purpose = "A compact session that still covers legs, push, pull and core.",
        standalone = true,
        categories = setOf(ExploreCategory.FULL_BODY, ExploreCategory.SHORT, ExploreCategory.BODYWEIGHT),
        slots = listOf(
            warm("warm_1", "step_jacks", "march_in_place", seconds = 40),
            main("squat", "Squat", SQUAT, "bodyweight_squat", "goblet_squat", "wall_sit", sets = 2, rest = 40),
            main("push", "Push", PUSH, "knee_push_up", "push_up", "incline_push_up", "wall_push_up", sets = 2, rest = 40),
            main("pull", "Pull", PULL, "db_one_arm_row", "prone_y_raise", sets = 2, rest = 40),
            main("core", "Core", CORE, "dead_bug", "knee_plank", sets = 2, reps = 6..8, rest = 30),
            cool("cool_1", "childs_pose", "standing_hamstring_stretch"),
        ),
    ),
    SessionTemplate(
        id = "quiet_twenty",
        name = "Quiet 20",
        focus = "Full body",
        purpose = "No jumping or stomping: suitable for flats, early mornings and late evenings.",
        standalone = true,
        categories = setOf(ExploreCategory.BODYWEIGHT, ExploreCategory.FULL_BODY),
        slots = listOf(
            warm("warm_1", "march_in_place", seconds = 45),
            warm("warm_2", "hip_hinge_drill", reps = 8),
            main("squat", "Squat", SQUAT, "wall_sit", "bodyweight_squat", "goblet_squat", sets = 3, seconds = 30, rest = 45),
            main("lunge", "Single-leg", LUNGE, "split_squat", "supported_split_squat", sets = 2, rest = 45),
            main("push", "Push", PUSH, "knee_push_up", "push_up", "wall_push_up", sets = 3, rest = 45),
            main("hinge", "Hinge", HINGE, "glute_bridge", "single_leg_glute_bridge", sets = 2, rest = 45),
            main("core", "Core", CORE, "forearm_plank", "knee_plank", sets = 2, seconds = 30, rest = 30),
            cool("cool_1", "figure_four_stretch", "childs_pose"),
        ),
    ),
    SessionTemplate(
        id = "mobility_reset",
        name = "10-minute mobility reset",
        focus = "Mobility",
        purpose = "Loosen up the hips, back and shoulders. A good choice on rest days.",
        standalone = true,
        categories = setOf(ExploreCategory.MOBILITY, ExploreCategory.SHORT, ExploreCategory.BEGINNER),
        slots = listOf(
            main("flow_1", "Spine", MOBILITY, "cat_cow", sets = 1, reps = 8..10, rest = 15),
            main("flow_2", "Upper back", MOBILITY, "open_book", sets = 1, reps = 5..6, rest = 15),
            main("flow_3", "Hips", MOBILITY, "lunge_with_reach", sets = 1, reps = 4..6, rest = 15),
            cool("cool_1", "hip_flexor_stretch"),
            cool("cool_2", "figure_four_stretch"),
            cool("cool_3", "childs_pose", seconds = 45),
        ),
    ),
    SessionTemplate(
        id = "desk_break",
        name = "Desk break",
        focus = "Mobility",
        purpose = "Eight standing minutes: no floor work and no equipment.",
        standalone = true,
        categories = setOf(ExploreCategory.MOBILITY, ExploreCategory.SHORT),
        slots = listOf(
            warm("warm_1", "march_in_place", seconds = 60),
            warm("warm_2", "arm_circles", seconds = 40),
            main("hinge", "Hinge", setOf(MovementPattern.WARMUP), "hip_hinge_drill", sets = 1, reps = 8..10, rest = 15),
            main("squat", "Squat", SQUAT, "bodyweight_squat", sets = 1, reps = 8..10, rest = 15),
            main("hips", "Hips", MOBILITY, "lunge_with_reach", sets = 1, reps = 4..5, rest = 15),
            cool("cool_1", "standing_hamstring_stretch", seconds = 30),
        ),
    ),
    SessionTemplate(
        id = "dumbbell_express",
        name = "20-minute dumbbell express",
        focus = "Strength",
        purpose = "Four big dumbbell movements in a short, focused session.",
        standalone = true,
        access = Access.PRO,
        level = ExperienceLevel.SOME,
        categories = setOf(ExploreCategory.DUMBBELLS, ExploreCategory.SHORT, ExploreCategory.FULL_BODY),
        slots = listOf(
            warm("warm_1", "march_in_place", "step_jacks", seconds = 45),
            main("squat", "Squat", SQUAT, "goblet_squat", sets = 3, reps = 8..12, rest = 60),
            main("press", "Push", PRESS, "db_floor_press", "db_shoulder_press", sets = 3, reps = 8..12, rest = 60),
            main("hinge", "Hinge", HINGE, "db_romanian_deadlift", sets = 3, reps = 8..12, rest = 60),
            main("row", "Pull", PULL, "db_bent_over_row", "db_one_arm_row", sets = 3, reps = 8..12, rest = 60),
            cool("cool_1", "standing_hamstring_stretch"),
        ),
    ),
    SessionTemplate(
        id = "core_twelve",
        name = "12-minute core",
        focus = "Core",
        purpose = "Trunk stability from several angles, all on the floor.",
        standalone = true,
        access = Access.PRO,
        categories = setOf(ExploreCategory.BODYWEIGHT, ExploreCategory.SHORT),
        slots = listOf(
            warm("warm_1", "cat_cow", reps = 8),
            main("core_1", "Core", CORE, "dead_bug", sets = 2, reps = 6..10, rest = 30),
            main("core_2", "Core", CORE, "forearm_plank", "knee_plank", sets = 2, seconds = 30, rest = 30),
            main("core_3", "Core", CORE, "side_plank_knees", sets = 2, seconds = 20, rest = 30),
            main("core_4", "Core", CORE, "bird_dog", sets = 2, reps = 6..10, rest = 30),
            cool("cool_1", "childs_pose", seconds = 45),
        ),
    ),
)

val DraftPrograms: List<ProgramTemplate> = listOf(Foundations, DumbbellStrength, SteadyHabit)
