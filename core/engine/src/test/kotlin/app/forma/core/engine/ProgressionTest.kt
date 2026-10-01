package app.forma.core.engine

import app.forma.core.model.ChangeKind
import app.forma.core.model.Difficulty
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.ExperienceLevel
import app.forma.core.model.Load
import app.forma.core.model.NoteKind
import app.forma.core.model.Target
import app.forma.core.model.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Acceptance criterion 5: progression uses actual performance, explained in plain language. */
class ProgressionTest {

    private fun squat(plan: app.forma.core.model.WorkoutPlan) = plan.items.single { it.key == "squat" }

    @Test
    fun `hitting the top of the range comfortably increases the load by one available step`() {
        val first = Fixtures.generate().plan
        assertEquals("goblet_squat", squat(first).exerciseId)
        assertEquals(Load.kg(2.5), squat(first).load)
        val history = TrainingHistory.from(listOf(Fixtures.perform(first, "s1", day = 1)))
        val next = Fixtures.generate(history = history).plan
        assertEquals(Load.kg(5.0), squat(next).load)
        assertEquals(ChangeKind.LOAD_UP, squat(next).change?.kind)
        assertTrue(next.notes.first { it.kind == NoteKind.SUMMARY }.text.contains("small progression"))
    }

    @Test
    fun `progression starts from the load actually used, not the load that was planned`() {
        val first = Fixtures.generate().plan
        val usedHeavier = Fixtures.perform(first, "s1", day = 1, loadOverride = { item ->
            if (item.key == "squat") Load.kg(10.0) else item.load
        })
        val next = Fixtures.generate(history = TrainingHistory.from(listOf(usedHeavier))).plan
        assertEquals(Load.kg(12.5), squat(next).load)
    }

    @Test
    fun `reps below target hold the prescription and twice in a row reduce it`() {
        val first = Fixtures.generate().plan
        val heavy = Fixtures.perform(first, "s1", day = 1, loadOverride = { if (it.key == "squat") Load.kg(10.0) else it.load }, repsFor = { 6 })
        val second = Fixtures.generate(history = TrainingHistory.from(listOf(heavy))).plan
        assertEquals(Load.kg(10.0), squat(second).load)
        assertEquals(ChangeKind.HOLD, squat(second).change?.kind)

        val heavyAgain = Fixtures.perform(second, "s2", day = 3, repsFor = { 6 })
        val third = Fixtures.generate(history = TrainingHistory.from(listOf(heavy, heavyAgain))).plan
        assertEquals(Load.kg(7.5), squat(third).load)
        assertEquals(ChangeKind.LOAD_DOWN, squat(third).change?.kind)
    }

    @Test
    fun `a too-hard rating reduces the next session even when reps were met`() {
        val first = Fixtures.generate().plan
        val session = Fixtures.perform(first, "s1", day = 1, difficulty = Difficulty.TOO_HARD,
            loadOverride = { if (it.key == "squat") Load.kg(10.0) else it.load })
        val next = Fixtures.generate(history = TrainingHistory.from(listOf(session))).plan
        assertEquals(Load.kg(7.5), squat(next).load)
        assertTrue(squat(next).change!!.message.contains("too hard"))
    }

    @Test
    fun `a hard rating holds the plan`() {
        val first = Fixtures.generate().plan
        val session = Fixtures.perform(first, "s1", day = 1, difficulty = Difficulty.HARD)
        val next = Fixtures.generate(history = TrainingHistory.from(listOf(session))).plan
        assertEquals(squat(first).load, squat(next).load)
        assertEquals(ChangeKind.HOLD, squat(next).change?.kind)
    }

    @Test
    fun `a big jump to the next dumbbell adds reps instead`() {
        val equipment = EquipmentProfile("fx", "Fixed", WeightUnit.KG, listOf(
            EquipmentItem.FixedDumbbells(500, 2), EquipmentItem.FixedDumbbells(1000, 2),
        ))
        val first = Fixtures.generate("foundations_a", equipment = equipment).plan
        assertEquals(Load.kg(5.0), squat(first).load)
        val history = TrainingHistory.from(listOf(Fixtures.perform(first, "s1", day = 1)))
        val next = Fixtures.generate("foundations_a", equipment = equipment, history = history).plan
        assertEquals(Load.kg(5.0), squat(next).load)
        assertEquals(ChangeKind.MORE_REPS, squat(next).change?.kind)
        val target = squat(next).target as Target.Reps
        assertEquals(14, target.max)
        assertTrue(squat(next).change!!.message.contains("big jump"))
    }

    @Test
    fun `timed holds get longer and bodyweight exercises move to a harder variation`() {
        val profile = Fixtures.profile(ExperienceLevel.SOME)
        val first = Fixtures.generate("foundations_a", equipment = Fixtures.bodyweight, profile = profile).plan
        assertEquals("bodyweight_squat", squat(first).exerciseId)
        val history = TrainingHistory.from(listOf(Fixtures.perform(first, "s1", day = 1)))
        val next = Fixtures.generate("foundations_a", equipment = Fixtures.bodyweight, profile = profile, history = history).plan
        assertEquals("tempo_squat", squat(next).exerciseId)
        assertEquals(ChangeKind.HARDER_VARIATION, squat(next).change?.kind)

        val core = Fixtures.generate("quiet_twenty", equipment = Fixtures.bodyweight, profile = profile).plan
        val plank = core.items.single { it.key == "core" }
        val coreHistory = TrainingHistory.from(listOf(Fixtures.perform(core, "c1", day = 1)))
        val nextCore = Fixtures.generate("quiet_twenty", equipment = Fixtures.bodyweight, profile = profile, history = coreHistory).plan
        val nextPlank = nextCore.items.single { it.key == "core" }
        assertEquals((plank.target as Target.Time).seconds + 5, (nextPlank.target as Target.Time).seconds)
    }

    @Test
    fun `without adaptive access the plan carries forward but does not increase`() {
        val first = Fixtures.generate().plan
        val history = TrainingHistory.from(listOf(Fixtures.perform(first, "s1", day = 1)))
        val next = Fixtures.generate(history = history, adaptive = false).plan
        assertEquals(squat(first).load, squat(next).load)
        assertTrue(next.notes.any { it.kind == NoteKind.PRO_LIMIT })
    }

    @Test
    fun `reductions still apply without adaptive access`() {
        val first = Fixtures.generate().plan
        val session = Fixtures.perform(first, "s1", day = 1, difficulty = Difficulty.TOO_HARD,
            loadOverride = { if (it.key == "squat") Load.kg(10.0) else it.load })
        val next = Fixtures.generate(history = TrainingHistory.from(listOf(session)), adaptive = false).plan
        assertEquals(Load.kg(7.5), squat(next).load)
    }

    @Test
    fun `skipped exercises carry no progression signal`() {
        val first = Fixtures.generate().plan
        val session = Fixtures.perform(first, "s1", day = 1, setsDone = { if (it.key == "squat") 0 else it.sets })
        val next = Fixtures.generate(history = TrainingHistory.from(listOf(session))).plan
        assertEquals(squat(first).load, squat(next).load)
        assertNotEquals(ChangeKind.LOAD_UP, squat(next).change?.kind)
    }

    @Test
    fun `a shortened session does not permanently reduce the number of sets`() {
        val profile = Fixtures.profile(ExperienceLevel.EXPERIENCED)
        val full = Fixtures.generate(profile = profile).plan
        assertEquals(3, squat(full).sets)
        val short = Fixtures.generate(profile = profile, adjustments = app.forma.core.model.PlanAdjustments(targetMinutes = 20)).plan
        val shortSquat = assertNotNull(short.items.firstOrNull { it.key == "squat" })
        assertEquals(2, shortSquat.sets)
        val history = TrainingHistory.from(listOf(Fixtures.perform(short, "s1", day = 1, difficulty = Difficulty.HARD)))
        val next = Fixtures.generate(profile = profile, history = history).plan
        assertEquals(3, squat(next).sets)
    }

    @Test
    fun `pound-based equipment progresses in pounds`() {
        val first = Fixtures.generate(equipment = Fixtures.adjustableLb).plan
        assertEquals(Load.lb(5.0), squat(first).load)
        val history = TrainingHistory.from(listOf(Fixtures.perform(first, "s1", day = 1)))
        val next = Fixtures.generate(equipment = Fixtures.adjustableLb, history = history).plan
        assertEquals(Load.lb(10.0), squat(next).load)
    }
}
