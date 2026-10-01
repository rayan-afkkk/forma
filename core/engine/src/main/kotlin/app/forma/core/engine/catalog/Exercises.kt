package app.forma.core.engine.catalog

import app.forma.core.model.BenchNeed
import app.forma.core.model.DumbbellUse
import app.forma.core.model.EquipmentRequirement
import app.forma.core.model.Execution
import app.forma.core.model.Exercise
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.MovementPattern
import app.forma.core.model.NoiseLevel
import app.forma.core.model.Phase
import app.forma.core.model.SpaceNeed

/*
 * DRAFT CONTENT. Every exercise, cue, variation link and substitution below is draft programming
 * written for product development. It must be reviewed and signed off by a qualified fitness
 * professional before public release. See docs/CONTENT_REVIEW_CHECKLIST.md.
 */

private val WARMUP = setOf(Phase.WARMUP)
private val COOLDOWN = setOf(Phase.COOLDOWN)
private val MAIN = setOf(Phase.MAIN)
private val MOBILITY_ANY = setOf(Phase.WARMUP, Phase.MAIN, Phase.COOLDOWN)

private val BODYWEIGHT = EquipmentRequirement.None
private val PAIR = EquipmentRequirement(dumbbells = DumbbellUse.PAIR)
private val SINGLE = EquipmentRequirement(dumbbells = DumbbellUse.SINGLE)
private val GOBLET = EquipmentRequirement(dumbbells = DumbbellUse.GOBLET)
private val BENCH = EquipmentRequirement(bench = BenchNeed.REQUIRED)
private val PAIR_BENCH = EquipmentRequirement(dumbbells = DumbbellUse.PAIR, bench = BenchNeed.REQUIRED)

val DraftExercises: List<Exercise> = listOf(
    // ---------------------------------------------------------------- Warm-up and mobility
    Exercise(
        id = "march_in_place", name = "March in place", pattern = MovementPattern.WARMUP,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 45,
        family = "march", familyRank = 1, substitutes = listOf("step_jacks", "arm_circles"),
        summary = "An easy way to raise your heart rate and warm up your legs.",
        cues = listOf("Lift your knees to a comfortable height", "Swing your arms naturally", "Breathe steadily"),
    ),
    Exercise(
        id = "step_jacks", name = "Step jacks", pattern = MovementPattern.WARMUP,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30,
        family = "jacks", familyRank = 1, substitutes = listOf("march_in_place"),
        summary = "A quiet, low-impact version of jumping jacks.",
        cues = listOf("Step one foot out to the side", "Raise your arms as you step", "Alternate sides at a steady pace"),
    ),
    Exercise(
        id = "jumping_jacks", name = "Jumping jacks", pattern = MovementPattern.WARMUP,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, noise = NoiseLevel.LOUD,
        family = "jacks", familyRank = 2, substitutes = listOf("step_jacks", "march_in_place"),
        summary = "A classic full-body warm-up with small jumps.",
        cues = listOf("Land softly on the balls of your feet", "Keep your knees slightly bent", "Find a rhythm you can keep"),
    ),
    Exercise(
        id = "arm_circles", name = "Arm circles", pattern = MovementPattern.WARMUP,
        phases = WARMUP, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30,
        family = "arm_circles", familyRank = 1, substitutes = listOf("march_in_place"),
        summary = "Loosens the shoulders before pushing and pulling.",
        cues = listOf("Start with small circles", "Gradually make them larger", "Switch direction halfway"),
    ),
    Exercise(
        id = "hip_hinge_drill", name = "Hip hinge drill", pattern = MovementPattern.WARMUP,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 10,
        family = "hinge_drill", familyRank = 1, substitutes = listOf("lunge_with_reach", "cat_cow"),
        summary = "Practises pushing the hips back while keeping a long back.",
        cues = listOf("Hands on your hips, soft knees", "Push your hips back as your chest tips forward", "Stand tall by squeezing your glutes"),
    ),
    Exercise(
        id = "lunge_with_reach", name = "Lunge with reach", pattern = MovementPattern.MOBILITY,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 4, defaultRepsMax = 6, perSide = true,
        space = SpaceNeed.MEDIUM,
        family = "lunge_reach", familyRank = 1, substitutes = listOf("hip_hinge_drill", "open_book"),
        summary = "Opens the hips and upper back in one flowing movement.",
        cues = listOf("Step back into a comfortable lunge", "Reach the same-side arm overhead", "Return to standing and switch sides"),
    ),
    Exercise(
        id = "cat_cow", name = "Cat–cow", pattern = MovementPattern.MOBILITY,
        phases = MOBILITY_ANY, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 10, floor = true,
        family = "cat_cow", familyRank = 1, substitutes = listOf("open_book", "childs_pose"),
        summary = "Gently moves the spine through bending and arching.",
        cues = listOf("Hands under shoulders, knees under hips", "Round your back as you breathe out", "Let your chest sink as you breathe in"),
    ),
    Exercise(
        id = "open_book", name = "Open book rotation", pattern = MovementPattern.MOBILITY,
        phases = MOBILITY_ANY, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 5, defaultRepsMax = 6, perSide = true, floor = true,
        family = "open_book", familyRank = 1, substitutes = listOf("cat_cow", "lunge_with_reach"),
        summary = "Encourages rotation through the upper back.",
        cues = listOf("Lie on your side with knees bent", "Open your top arm across like a book", "Follow your hand with your eyes"),
    ),

    // ---------------------------------------------------------------- Cool-down stretches
    Exercise(
        id = "hip_flexor_stretch", name = "Half-kneeling hip flexor stretch", pattern = MovementPattern.STRETCH,
        phases = COOLDOWN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, perSide = true, floor = true,
        family = "hip_flexor_stretch", familyRank = 1, substitutes = listOf("figure_four_stretch", "standing_hamstring_stretch"),
        summary = "Stretches the front of the hip after squats and lunges.",
        cues = listOf("Kneel on one knee, other foot forward", "Tuck your hips under slightly", "Breathe and hold without bouncing"),
    ),
    Exercise(
        id = "standing_hamstring_stretch", name = "Standing hamstring stretch", pattern = MovementPattern.STRETCH,
        phases = COOLDOWN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, perSide = true,
        family = "hamstring_stretch", familyRank = 1, substitutes = listOf("figure_four_stretch", "childs_pose"),
        summary = "Stretches the back of the leg without getting on the floor.",
        cues = listOf("Place one heel slightly in front", "Hinge forward with a long back", "Stop at a gentle stretch"),
    ),
    Exercise(
        id = "childs_pose", name = "Child's pose", pattern = MovementPattern.STRETCH,
        phases = COOLDOWN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 40, floor = true,
        family = "childs_pose", familyRank = 1, substitutes = listOf("standing_hamstring_stretch", "cat_cow"),
        summary = "A restful stretch for the back and hips.",
        cues = listOf("Sit your hips back toward your heels", "Reach your arms forward", "Breathe slowly into your back"),
    ),
    Exercise(
        id = "figure_four_stretch", name = "Figure-four stretch", pattern = MovementPattern.STRETCH,
        phases = COOLDOWN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, perSide = true, floor = true,
        family = "figure_four", familyRank = 1, substitutes = listOf("hip_flexor_stretch", "standing_hamstring_stretch"),
        summary = "Stretches the outer hip and glutes.",
        cues = listOf("Lie on your back and cross one ankle over the other knee", "Draw the legs gently toward you", "Keep your head and shoulders relaxed"),
    ),

    // ---------------------------------------------------------------- Squat
    Exercise(
        id = "box_squat", name = "Squat to bench", pattern = MovementPattern.SQUAT,
        phases = MAIN, equipment = BENCH, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "squat", familyRank = 1, substitutes = listOf("bodyweight_squat", "wall_sit"),
        summary = "A squat with a bench behind you as a depth target.",
        cues = listOf("Stand just in front of the bench", "Sit back until you lightly touch it", "Stand up by pushing through your whole foot"),
    ),
    Exercise(
        id = "bodyweight_squat", name = "Bodyweight squat", pattern = MovementPattern.SQUAT,
        phases = WARMUP + MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15,
        family = "squat", familyRank = 2, substitutes = listOf("wall_sit", "box_squat", "goblet_squat"),
        summary = "The foundation of lower-body strength.",
        cues = listOf("Feet about shoulder-width apart", "Sit down between your heels", "Keep your knees tracking over your toes"),
    ),
    Exercise(
        id = "tempo_squat", name = "Slow-lowering squat", pattern = MovementPattern.SQUAT,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "squat", familyRank = 3, substitutes = listOf("bodyweight_squat", "split_squat"),
        summary = "A squat with a slow, controlled lowering to make bodyweight harder.",
        cues = listOf("Take about three seconds to lower", "Pause briefly at the bottom", "Stand up at a normal pace"),
    ),
    Exercise(
        id = "goblet_squat", name = "Goblet squat", pattern = MovementPattern.SQUAT,
        phases = MAIN, equipment = GOBLET, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "squat", familyRank = 3, substitutes = listOf("bodyweight_squat", "tempo_squat", "db_split_squat"),
        summary = "A squat holding one dumbbell at your chest.",
        cues = listOf("Hold one end of the dumbbell at your chest", "Keep your elbows pointing down", "Sit down between your heels and stand tall"),
    ),
    Exercise(
        id = "wall_sit", name = "Wall sit", pattern = MovementPattern.SQUAT,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30,
        family = "wall_sit", familyRank = 1, substitutes = listOf("bodyweight_squat", "box_squat"),
        summary = "A quiet squat hold against a wall.",
        cues = listOf("Slide down the wall to a comfortable depth", "Keep your weight in your heels", "Breathe steadily while you hold"),
    ),

    // ---------------------------------------------------------------- Single-leg
    Exercise(
        id = "supported_split_squat", name = "Supported split squat", pattern = MovementPattern.LUNGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 10, perSide = true,
        family = "split_squat", familyRank = 1, substitutes = listOf("bodyweight_squat", "split_squat"),
        summary = "A split squat with one hand on a wall for balance.",
        cues = listOf("Stand in a long split stance beside a wall", "Lower your back knee toward the floor", "Push through the front foot to rise"),
    ),
    Exercise(
        id = "split_squat", name = "Split squat", pattern = MovementPattern.LUNGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 10, perSide = true,
        family = "split_squat", familyRank = 2, substitutes = listOf("supported_split_squat", "reverse_lunge", "bodyweight_squat"),
        summary = "Builds single-leg strength and balance without stepping.",
        cues = listOf("Long split stance, feet hip-width apart", "Lower straight down, not forward", "Keep your front heel on the floor"),
    ),
    Exercise(
        id = "reverse_lunge", name = "Reverse lunge", pattern = MovementPattern.LUNGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 10, perSide = true,
        space = SpaceNeed.MEDIUM,
        family = "split_squat", familyRank = 3, substitutes = listOf("split_squat", "db_split_squat"),
        summary = "Step back into a lunge, then return to standing.",
        cues = listOf("Step back far enough for both knees to bend", "Lower with control", "Drive through the front foot to step back in"),
    ),
    Exercise(
        id = "db_split_squat", name = "Dumbbell split squat", pattern = MovementPattern.LUNGE,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 10, perSide = true,
        family = "split_squat", familyRank = 4, substitutes = listOf("split_squat", "goblet_squat", "reverse_lunge"),
        summary = "A split squat holding a dumbbell in each hand.",
        cues = listOf("Let the dumbbells hang by your sides", "Lower straight down under control", "Keep your torso tall"),
    ),

    // ---------------------------------------------------------------- Hinge
    Exercise(
        id = "glute_bridge", name = "Glute bridge", pattern = MovementPattern.HINGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15, floor = true,
        family = "bridge", familyRank = 1, substitutes = listOf("db_glute_bridge", "single_leg_glute_bridge", "db_romanian_deadlift"),
        summary = "Strengthens the glutes and the back of the legs.",
        cues = listOf("Lie on your back, feet flat, knees bent", "Press through your heels to lift your hips", "Pause at the top without arching your lower back"),
    ),
    Exercise(
        id = "db_glute_bridge", name = "Dumbbell glute bridge", pattern = MovementPattern.HINGE,
        phases = MAIN, equipment = GOBLET, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15, floor = true,
        family = "bridge", familyRank = 2, substitutes = listOf("glute_bridge", "single_leg_glute_bridge"),
        summary = "A glute bridge with a dumbbell resting across your hips.",
        cues = listOf("Hold the dumbbell steady on your hips", "Press through your heels to lift", "Lower slowly"),
    ),
    Exercise(
        id = "single_leg_glute_bridge", name = "Single-leg glute bridge", pattern = MovementPattern.HINGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12, perSide = true, floor = true,
        family = "bridge", familyRank = 3, substitutes = listOf("glute_bridge", "db_glute_bridge"),
        summary = "A bridge on one leg to make bodyweight harder.",
        cues = listOf("Lift one foot off the floor", "Keep your hips level as you rise", "Lower with control"),
    ),
    Exercise(
        id = "single_leg_hip_hinge", name = "Single-leg hip hinge", pattern = MovementPattern.HINGE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 10, perSide = true,
        family = "rdl", familyRank = 1, substitutes = listOf("glute_bridge", "db_romanian_deadlift"),
        summary = "A balance-and-hinge pattern on one leg. Use a wall for support if needed.",
        cues = listOf("Soft knee on the standing leg", "Reach the free leg back as you tip forward", "Keep your hips square to the floor"),
    ),
    Exercise(
        id = "db_romanian_deadlift", name = "Dumbbell Romanian deadlift", pattern = MovementPattern.HINGE,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "rdl", familyRank = 2, substitutes = listOf("glute_bridge", "db_glute_bridge", "single_leg_hip_hinge"),
        summary = "A hip hinge holding dumbbells, for the hamstrings and glutes.",
        cues = listOf("Soft knees, dumbbells in front of your thighs", "Push your hips back and slide the weights down your legs", "Stand tall by squeezing your glutes"),
    ),

    // ---------------------------------------------------------------- Horizontal push
    Exercise(
        id = "wall_push_up", name = "Wall push-up", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15,
        family = "push_up", familyRank = 1, substitutes = listOf("incline_push_up", "knee_push_up"),
        summary = "The most approachable push-up, done against a wall.",
        cues = listOf("Hands on the wall at chest height", "Keep a straight line from head to heels", "Lower your chest toward the wall and press away"),
    ),
    Exercise(
        id = "incline_push_up", name = "Incline push-up", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = BENCH, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "push_up", familyRank = 2, substitutes = listOf("knee_push_up", "wall_push_up", "push_up"),
        summary = "A push-up with your hands on a bench.",
        cues = listOf("Hands on the bench edge, shoulder-width apart", "Body in one straight line", "Lower your chest to the bench and press away"),
    ),
    Exercise(
        id = "knee_push_up", name = "Kneeling push-up", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 12, floor = true,
        family = "push_up", familyRank = 2, substitutes = listOf("wall_push_up", "incline_push_up", "push_up"),
        summary = "A push-up from your knees to build pressing strength.",
        cues = listOf("Knees on the floor, hands under shoulders", "Keep a straight line from head to knees", "Lower with control and press up"),
    ),
    Exercise(
        id = "push_up", name = "Push-up", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 12, floor = true,
        family = "push_up", familyRank = 3, substitutes = listOf("knee_push_up", "incline_push_up", "db_floor_press"),
        summary = "A full push-up from your toes.",
        cues = listOf("Hands slightly wider than shoulders", "Brace your middle so your hips don't sag", "Lower until your chest is near the floor"),
    ),
    Exercise(
        id = "db_floor_press", name = "Dumbbell floor press", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12, floor = true,
        family = "db_press", familyRank = 1, substitutes = listOf("db_bench_press", "knee_push_up", "push_up"),
        summary = "A chest press lying on the floor, no bench needed.",
        cues = listOf("Lie on your back with knees bent", "Lower until your upper arms touch the floor", "Press the dumbbells up over your chest"),
    ),
    Exercise(
        id = "db_bench_press", name = "Dumbbell bench press", pattern = MovementPattern.PUSH_HORIZONTAL,
        phases = MAIN, equipment = PAIR_BENCH, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "db_press", familyRank = 2, substitutes = listOf("db_floor_press", "push_up"),
        summary = "A chest press lying on a bench for a longer range of motion.",
        cues = listOf("Feet flat, shoulder blades set on the bench", "Lower the dumbbells beside your chest", "Press up and slightly together"),
    ),

    // ---------------------------------------------------------------- Overhead push
    Exercise(
        id = "db_shoulder_press", name = "Dumbbell shoulder press", pattern = MovementPattern.PUSH_VERTICAL,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "overhead", familyRank = 2, substitutes = listOf("pike_push_up", "db_floor_press", "knee_push_up"),
        summary = "Presses dumbbells overhead, standing or seated.",
        cues = listOf("Start with the dumbbells at shoulder height", "Brace your middle and press overhead", "Lower slowly back to your shoulders"),
    ),
    Exercise(
        id = "pike_push_up", name = "Pike push-up", pattern = MovementPattern.PUSH_VERTICAL,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 5, defaultRepsMax = 10, floor = true,
        family = "overhead", familyRank = 3, substitutes = listOf("push_up", "knee_push_up", "db_shoulder_press"),
        summary = "A bodyweight overhead press with your hips raised.",
        cues = listOf("Hips high, hands and feet on the floor", "Bend your elbows to lower your head forward", "Press back up to the start"),
    ),

    // ---------------------------------------------------------------- Pull and upper back
    Exercise(
        id = "db_one_arm_row", name = "One-arm dumbbell row", pattern = MovementPattern.PULL,
        phases = MAIN, equipment = SINGLE, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12, perSide = true,
        family = "row", familyRank = 1, substitutes = listOf("db_bent_over_row", "db_reverse_fly", "prone_y_raise"),
        summary = "Rows one dumbbell with your other hand supported on a bench or your thigh.",
        cues = listOf("Support yourself on a bench or your front thigh", "Pull the dumbbell toward your hip", "Lower it slowly until your arm is straight"),
    ),
    Exercise(
        id = "db_bent_over_row", name = "Dumbbell bent-over row", pattern = MovementPattern.PULL,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12,
        family = "row", familyRank = 2, substitutes = listOf("db_one_arm_row", "db_reverse_fly", "prone_y_raise"),
        summary = "Rows both dumbbells from a hip-hinged position.",
        cues = listOf("Hinge forward with a long back", "Pull both dumbbells toward your ribs", "Lower with control"),
    ),
    Exercise(
        id = "db_reverse_fly", name = "Bent-over reverse fly", pattern = MovementPattern.PULL,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15,
        family = "rear_fly", familyRank = 1, substitutes = listOf("prone_y_raise", "db_one_arm_row"),
        summary = "Light dumbbells raised out to the sides for the upper back.",
        cues = listOf("Hinge forward, arms hanging", "Raise the dumbbells out wide with soft elbows", "Squeeze your shoulder blades, then lower"),
    ),
    Exercise(
        id = "prone_y_raise", name = "Prone Y raise", pattern = MovementPattern.PULL,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 8, defaultRepsMax = 12, floor = true,
        family = "prone", familyRank = 1, substitutes = listOf("db_reverse_fly", "db_one_arm_row"),
        summary = "Strengthens the upper back without equipment.",
        cues = listOf("Lie face down, arms overhead in a Y", "Lift your arms a few centimetres off the floor", "Keep your neck long and lower slowly"),
    ),

    // ---------------------------------------------------------------- Core
    Exercise(
        id = "dead_bug", name = "Dead bug", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 10, perSide = true, floor = true,
        family = "dead_bug", familyRank = 1, substitutes = listOf("bird_dog", "knee_plank"),
        summary = "Trains your middle to stay steady while your limbs move.",
        cues = listOf("Lie on your back, arms up, knees over hips", "Lower the opposite arm and leg slowly", "Keep your lower back gently pressed down"),
    ),
    Exercise(
        id = "bird_dog", name = "Bird dog", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 10, perSide = true, floor = true,
        family = "bird_dog", familyRank = 1, substitutes = listOf("dead_bug", "knee_plank"),
        summary = "Builds steadiness through the trunk on hands and knees.",
        cues = listOf("Hands under shoulders, knees under hips", "Reach the opposite arm and leg long", "Keep your hips level"),
    ),
    Exercise(
        id = "knee_plank", name = "Kneeling plank", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 20, floor = true,
        family = "plank", familyRank = 1, substitutes = listOf("dead_bug", "bird_dog"),
        summary = "A shorter-lever plank from your knees.",
        cues = listOf("Forearms under shoulders, knees down", "Straight line from head to knees", "Breathe steadily"),
    ),
    Exercise(
        id = "forearm_plank", name = "Forearm plank", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, floor = true,
        family = "plank", familyRank = 2, substitutes = listOf("knee_plank", "dead_bug"),
        summary = "Holds a straight body position on your forearms and toes.",
        cues = listOf("Forearms under shoulders", "Squeeze your glutes and brace your middle", "Stop if your hips start to sag"),
    ),
    Exercise(
        id = "side_plank_knees", name = "Side plank from knees", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 20, perSide = true, floor = true,
        family = "side_plank", familyRank = 1, substitutes = listOf("suitcase_hold", "bird_dog"),
        summary = "Trains the sides of your trunk.",
        cues = listOf("Elbow under shoulder, knees bent", "Lift your hips into a straight line", "Hold and breathe"),
    ),
    Exercise(
        id = "suitcase_hold", name = "Suitcase hold", pattern = MovementPattern.CORE,
        phases = MAIN, equipment = SINGLE, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 30, perSide = true,
        family = "carry", familyRank = 1, substitutes = listOf("side_plank_knees", "bird_dog"),
        summary = "Stand tall holding one dumbbell at your side.",
        cues = listOf("Hold one dumbbell by your side", "Stand tall without leaning", "Switch hands halfway through"),
    ),

    // ---------------------------------------------------------------- Arms
    Exercise(
        id = "db_curl", name = "Dumbbell curl", pattern = MovementPattern.ARMS,
        phases = MAIN, equipment = PAIR, minLevel = ExperienceLevel.NEW,
        execution = Execution.REPS, defaultRepsMin = 10, defaultRepsMax = 15,
        family = "curl", familyRank = 1, substitutes = listOf("db_one_arm_row"),
        summary = "Works the front of the upper arms.",
        cues = listOf("Elbows close to your sides", "Curl the dumbbells up without swinging", "Lower slowly"),
    ),

    // ---------------------------------------------------------------- Conditioning
    Exercise(
        id = "shadow_boxing", name = "Shadow boxing", pattern = MovementPattern.CONDITIONING,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.NEW,
        execution = Execution.TIME, defaultSeconds = 40,
        family = "shadow_boxing", familyRank = 1, substitutes = listOf("march_in_place", "step_jacks"),
        summary = "Light punches with steady footwork. Quiet and easy to scale.",
        cues = listOf("Soft knees, guard up", "Punch at a pace you can keep", "Keep breathing out with each punch"),
    ),
    Exercise(
        id = "mountain_climber", name = "Mountain climber", pattern = MovementPattern.CONDITIONING,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.TIME, defaultSeconds = 30, noise = NoiseLevel.MODERATE, floor = true,
        family = "climber", familyRank = 1, substitutes = listOf("shadow_boxing", "step_jacks"),
        summary = "Drive your knees toward your chest from a push-up position.",
        cues = listOf("Hands under shoulders", "Alternate knees at a steady pace", "Keep your hips level"),
    ),
    Exercise(
        id = "squat_jump", name = "Squat jump", pattern = MovementPattern.CONDITIONING,
        phases = MAIN, equipment = BODYWEIGHT, minLevel = ExperienceLevel.SOME,
        execution = Execution.REPS, defaultRepsMin = 6, defaultRepsMax = 10, noise = NoiseLevel.LOUD,
        family = "jump", familyRank = 1, substitutes = listOf("bodyweight_squat", "shadow_boxing"),
        summary = "A squat with a small jump. Loud; skipped when you choose quiet workouts.",
        cues = listOf("Squat down, then jump up lightly", "Land softly with bent knees", "Reset before each jump"),
    ),
)
