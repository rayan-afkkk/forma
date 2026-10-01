package app.forma.core.engine

import app.forma.core.model.BenchNeed
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.NoiseLevel
import app.forma.core.model.NoteKind
import app.forma.core.model.PlanAdjustments
import app.forma.core.model.SpaceNeed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Acceptance criteria 1, 3 and 4: exclusions, equipment/weights, and conflict explanations. */
class ConstraintTest {
    private val catalog = Fixtures.catalog

    @Test
    fun `permanently excluded exercises never appear in generated workouts`() {
        for (template in Fixtures.allTemplates) {
            for (profile in Fixtures.allProfiles) {
                val baseline = Fixtures.generate(template.id, equipment = profile).plan
                // Exclude each exercise the generator picked, one at a time and all together.
                val picked = baseline.items.map { it.exerciseId }.toSet()
                for (excluded in picked) {
                    val plan = Fixtures.generate(template.id, equipment = profile, exclusions = setOf(excluded)).plan
                    assertTrue(plan.items.none { it.exerciseId == excluded }, "${template.id}/${profile.id} still has $excluded")
                }
                val plan = Fixtures.generate(template.id, equipment = profile, exclusions = picked).plan
                assertTrue(plan.items.none { it.exerciseId in picked })
            }
        }
    }

    @Test
    fun `temporary replacements cannot bring back an excluded exercise`() {
        val plan = Fixtures.generate(
            "foundations_a",
            equipment = Fixtures.bodyweight,
            exclusions = setOf("glute_bridge"),
            adjustments = PlanAdjustments(replacements = mapOf("hinge" to "glute_bridge")),
        ).plan
        assertTrue(plan.items.none { it.exerciseId == "glute_bridge" })
        assertTrue(plan.notes.any { it.kind == NoteKind.INFO })
    }

    @Test
    fun `plans never require equipment or weights the person does not have`() {
        for (template in Fixtures.allTemplates) {
            for (profile in Fixtures.allProfiles) {
                val plan = Fixtures.generate(template.id, equipment = profile, profile = Fixtures.profile(ExperienceLevel.EXPERIENCED)).plan
                for (item in plan.items) {
                    val exercise = catalog.requireExercise(item.exerciseId)
                    if (exercise.equipment.bench == BenchNeed.REQUIRED) {
                        assertTrue(profile.hasBench, "${template.id}/${profile.id}: ${item.exerciseId} needs a bench")
                    }
                    val use = exercise.equipment.dumbbells
                    if (use == null) {
                        assertEquals(null, item.load)
                    } else {
                        val load = assertNotNull(item.load, "${item.exerciseId} has no load")
                        assertTrue(load in EquipmentLoads.available(profile, use), "${profile.id}: $load not available for $use")
                        assertEquals(profile.unit, load.unit)
                    }
                }
            }
        }
    }

    @Test
    fun `a single fixed dumbbell is never prescribed as a pair`() {
        val plan = Fixtures.generate("strength_b", equipment = Fixtures.singleDumbbell, profile = Fixtures.profile(ExperienceLevel.SOME)).plan
        assertTrue(plan.items.none { it.dumbbellUse == app.forma.core.model.DumbbellUse.PAIR })
    }

    @Test
    fun `quiet and limited space are honoured as hard constraints`() {
        val profile = Fixtures.profile(ExperienceLevel.EXPERIENCED, quiet = true, limitedSpace = true)
        for (template in Fixtures.allTemplates) {
            val plan = Fixtures.generate(template.id, equipment = Fixtures.adjustableKg, profile = profile).plan
            for (item in plan.items) {
                val exercise = catalog.requireExercise(item.exerciseId)
                assertTrue(exercise.noise != NoiseLevel.LOUD, "${item.exerciseId} is loud")
                assertEquals(SpaceNeed.SMALL, exercise.space, "${item.exerciseId} needs space")
            }
        }
    }

    @Test
    fun `quiet override for one day removes loud exercises`() {
        val loud = Fixtures.generate("habit_conditioning", equipment = Fixtures.bodyweight).plan
        val quiet = Fixtures.generate("habit_conditioning", equipment = Fixtures.bodyweight, adjustments = PlanAdjustments(quiet = true)).plan
        assertTrue(quiet.items.none { catalog.requireExercise(it.exerciseId).noise == NoiseLevel.LOUD })
        assertTrue(loud.items.size >= quiet.items.size)
    }

    @Test
    fun `conflicting constraints produce a plain-language explanation instead of a silent violation`() {
        val pullOptions = catalog.exercises.filter { it.pattern == app.forma.core.model.MovementPattern.PULL }.map { it.id }.toSet()
        val plan = Fixtures.generate("foundations_a", equipment = Fixtures.bodyweight, exclusions = setOf("prone_y_raise")).plan
        assertTrue(plan.items.none { it.exerciseId in pullOptions })
        val note = plan.notes.single { it.kind == NoteKind.CONSTRAINT }
        assertTrue(note.text.contains("pull", ignoreCase = true), note.text)
        assertTrue(note.text.contains("needs dumbbells") || note.text.contains("need"), note.text)
        assertTrue(note.text.contains("excluded"), note.text)
    }

    @Test
    fun `level constraint keeps advanced variations away from beginners`() {
        val plan = Fixtures.generate("foundations_a", equipment = Fixtures.bodyweight, profile = Fixtures.profile(ExperienceLevel.NEW)).plan
        assertFalse(plan.items.any { catalog.requireExercise(it.exerciseId).minLevel.rank > ExperienceLevel.NEW.rank })
    }
}
