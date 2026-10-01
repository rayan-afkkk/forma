package app.forma.core.engine

import app.forma.core.model.ExperienceLevel
import app.forma.core.model.NoteKind
import app.forma.core.model.Phase
import app.forma.core.model.PlanAdjustments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdjustmentTest {

    @Test
    fun `a temporary replacement applies to this plan and the slot returns to the original next time`() {
        val profile = Fixtures.profile(ExperienceLevel.SOME)
        val replaced = Fixtures.generate(
            profile = profile,
            adjustments = PlanAdjustments(replacements = mapOf("push" to "push_up")),
        ).plan
        val push = replaced.items.single { it.key == "push" }
        assertEquals("push_up", push.exerciseId)
        assertEquals("db_floor_press", push.replacedFromId)

        val history = TrainingHistory.from(listOf(Fixtures.perform(replaced, "s1", day = 1)))
        val next = Fixtures.generate(profile = profile, history = history).plan
        assertEquals("db_floor_press", next.items.single { it.key == "push" }.exerciseId)
    }

    @Test
    fun `shortening removes optional work first and explains what changed`() {
        val profile = Fixtures.profile(ExperienceLevel.EXPERIENCED)
        val full = Fixtures.generate(profile = profile).plan
        val short = Fixtures.generate(profile = profile, adjustments = PlanAdjustments(targetMinutes = 20))
        assertNull(short.impractical)
        assertTrue(short.plan.estimatedSeconds <= 20 * 60 * 1.1, "estimated ${short.plan.estimatedSeconds}s")
        assertTrue(short.plan.estimatedSeconds < full.estimatedSeconds)
        assertTrue(short.plan.items.none { it.key == "core" })
        assertTrue(short.plan.notes.any { it.kind == NoteKind.SHORTENED && it.text.contains("Dead bug") })
    }

    @Test
    fun `an impractically short request is explained with the shortest practical version`() {
        val result = Fixtures.generate(profile = Fixtures.profile(ExperienceLevel.EXPERIENCED), adjustments = PlanAdjustments(targetMinutes = 5))
        val impractical = assertNotNull(result.impractical)
        assertTrue(impractical.shortestMinutes > 5)
        assertTrue(result.plan.notes.any { it.text.contains("wouldn't leave room") })
        assertTrue(result.plan.items.count { it.phase == Phase.MAIN } >= TrainingRules.MIN_MAIN_ITEMS_WHEN_SHORTENED)
    }

    @Test
    fun `easier session reduces main sets for today only`() {
        val profile = Fixtures.profile(ExperienceLevel.EXPERIENCED)
        val normal = Fixtures.generate(profile = profile).plan
        val easier = Fixtures.generate(profile = profile, adjustments = PlanAdjustments(easier = true)).plan
        val n = normal.items.single { it.key == "squat" }
        val e = easier.items.single { it.key == "squat" }
        assertEquals(n.sets - 1, e.sets)
        assertEquals(n.programmedSets, e.programmedSets)
        assertTrue(easier.notes.any { it.kind == NoteKind.EASIER })
    }

    @Test
    fun `make easier for one item picks an easier variation when one exists`() {
        val profile = Fixtures.profile(ExperienceLevel.SOME)
        val plan = Fixtures.generate(equipment = Fixtures.bodyweight, profile = profile, adjustments = PlanAdjustments(easierItems = setOf("push"))).plan
        val push = plan.items.single { it.key == "push" }
        assertEquals("knee_push_up", push.exerciseId)
        assertEquals("push_up", push.replacedFromId)
    }

    @Test
    fun `omitting an item removes it and says so`() {
        val plan = Fixtures.generate(adjustments = PlanAdjustments(omitted = setOf("pull"))).plan
        assertTrue(plan.items.none { it.key == "pull" })
        assertTrue(plan.notes.any { it.kind == NoteKind.OMITTED })
    }

    @Test
    fun `returning after a break eases main work for that session`() {
        val profile = Fixtures.profile(ExperienceLevel.EXPERIENCED)
        val normal = Fixtures.generate(profile = profile).plan
        val easing = Fixtures.generate(profile = profile, returnEase = true).plan
        assertEquals(normal.items.single { it.key == "squat" }.sets - 1, easing.items.single { it.key == "squat" }.sets)
        assertTrue(easing.notes.any { it.kind == NoteKind.RETURN })
    }

    @Test
    fun `replacement options respect constraints and the rest of the workout`() {
        val plan = Fixtures.generate(equipment = Fixtures.bodyweight).plan
        val constraints = Fixtures.generator.constraintsFor(Fixtures.profile(), Fixtures.bodyweight, emptySet())
        val squat = plan.items.single { it.key == "squat" }
        val others = plan.items.filter { it.key != "squat" }.map { it.exerciseId }.toSet()
        val result = assertIs<ReplacementResult.Available>(Fixtures.generator.substitutions.replacements(squat, others, constraints))
        for (option in result.options) {
            assertTrue(constraints.allows(option.exercise))
            assertTrue(option.exercise.id !in others)
            assertTrue(option.exercise.equipment.dumbbells == null)
        }
    }

    @Test
    fun `no suitable replacement is explained`() {
        val plan = Fixtures.generate(equipment = Fixtures.bodyweight).plan
        val pull = plan.items.single { it.key == "pull" }
        val constraints = Fixtures.generator.constraintsFor(Fixtures.profile(), Fixtures.bodyweight, emptySet())
        val result = assertIs<ReplacementResult.None>(Fixtures.generator.substitutions.replacements(pull, emptySet(), constraints))
        assertTrue(result.explanation.contains("leave Prone Y raise out"), result.explanation)
    }
}
