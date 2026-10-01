package app.forma.core.engine

import app.forma.core.model.Phase
import app.forma.core.model.ReviewStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogTest {
    private val catalog = Fixtures.catalog

    @Test
    fun `bundled catalog has no structural problems`() {
        assertEquals(emptyList(), catalog.validate())
    }

    @Test
    fun `catalog size and structure match the content plan`() {
        assertTrue(catalog.exercises.size in 25..50, "exercise count ${catalog.exercises.size}")
        assertEquals(1, catalog.programs.count { it.access == app.forma.core.model.Access.FREE })
        assertEquals(2, catalog.programs.count { it.access == app.forma.core.model.Access.PRO })
        assertTrue(catalog.standaloneSessions.size >= 4)
    }

    @Test
    fun `all bundled content is marked as draft pending trainer review`() {
        assertTrue(catalog.exercises.all { it.review == ReviewStatus.DRAFT })
    }

    @Test
    fun `every template produces main work for a fully equipped person`() {
        for (template in Fixtures.allTemplates) {
            val plan = Fixtures.generate(template.id, equipment = Fixtures.adjustableKg).plan
            assertTrue(plan.items.any { it.phase == Phase.MAIN }, "${template.id} has no main work")
            assertTrue(plan.estimatedSeconds > 0)
        }
    }

    @Test
    fun `the free starter program works with bodyweight only for a beginner`() {
        for (id in listOf("foundations_a", "foundations_b")) {
            val plan = Fixtures.generate(
                id,
                equipment = Fixtures.bodyweight,
                profile = Fixtures.profile(level = app.forma.core.model.ExperienceLevel.NEW),
            ).plan
            val mains = plan.items.filter { it.phase == Phase.MAIN }
            assertTrue(mains.size >= 4, "$id only has ${mains.size} main items: ${plan.notes}")
            assertTrue(mains.none { it.load != null })
        }
    }
}
