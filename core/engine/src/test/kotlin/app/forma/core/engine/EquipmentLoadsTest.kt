package app.forma.core.engine

import app.forma.core.model.DumbbellUse
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Load
import app.forma.core.model.WeightUnit
import app.forma.core.model.format
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Acceptance criterion 11 and the weight-availability rules behind criterion 3. */
class EquipmentLoadsTest {

    @Test
    fun `pairs require two dumbbells of the same weight`() {
        val profile = Fixtures.fixedPairs
        assertEquals(listOf(Load.kg(5.0), Load.kg(10.0)), EquipmentLoads.available(profile, DumbbellUse.PAIR))
        assertEquals(listOf(Load.kg(5.0), Load.kg(10.0), Load.kg(12.5)), EquipmentLoads.available(profile, DumbbellUse.SINGLE))
    }

    @Test
    fun `adjustable dumbbells produce every increment between min and max`() {
        val loads = EquipmentLoads.available(Fixtures.adjustableKg, DumbbellUse.PAIR)
        assertEquals(Load.kg(2.5), loads.first())
        assertEquals(Load.kg(20.0), loads.last())
        assertEquals(8, loads.size)
        assertTrue(loads.zipWithNext().all { (a, b) -> b.hundredths - a.hundredths == 250 })
    }

    @Test
    fun `snapping never rounds up to a weight that is heavier than intended`() {
        val loads = EquipmentLoads.available(Fixtures.fixedPairs, DumbbellUse.PAIR)
        assertEquals(Load.kg(5.0), EquipmentLoads.snap(loads, Load.kg(8.0)))
        assertEquals(Load.kg(10.0), EquipmentLoads.snap(loads, Load.lb(25.0))) // 25 lb ≈ 11.3 kg
        assertEquals(Load.kg(5.0), EquipmentLoads.snap(loads, Load.kg(1.0)))
    }

    @Test
    fun `load jumps are limited`() {
        assertTrue(EquipmentLoads.isReasonableJump(Load.kg(2.0), Load.kg(4.0)))
        assertTrue(EquipmentLoads.isReasonableJump(Load.kg(20.0), Load.kg(25.0)))
        assertFalse(EquipmentLoads.isReasonableJump(Load.kg(5.0), Load.kg(10.0)))
        assertTrue(EquipmentLoads.isReasonableJump(Load.lb(20.0), Load.lb(25.0)))
    }

    @Test
    fun `stored loads keep their unit and display conversion is marked approximate`() {
        val stored = Load.kg(10.0)
        assertEquals("10 kg", stored.format())
        assertEquals("≈22 lb", stored.format(WeightUnit.LB))
        assertEquals(1000, stored.hundredths)
        assertEquals(WeightUnit.KG, stored.unit)
        val pounds = Load.lb(25.0)
        assertEquals("≈11.3 kg", pounds.format(WeightUnit.KG))
        assertEquals(Load.lb(25.0), pounds)
        assertEquals("12.5 kg", Load.kg(12.5).format())
        assertEquals("1.25 kg", Load.kg(1.25).format())
    }

    @Test
    fun `equipment descriptions are honest about what is owned`() {
        assertEquals("Bodyweight only", EquipmentLoads.describe(Fixtures.bodyweight))
        assertEquals("Single dumbbell 8 kg", EquipmentLoads.describe(Fixtures.singleDumbbell))
        assertEquals("Dumbbells 2.5–20 kg · Bench", EquipmentLoads.describe(Fixtures.adjustableKg))
        val mixed = EquipmentProfile("m", "Mixed", WeightUnit.LB, listOf(EquipmentItem.FixedDumbbells(1500, 2)))
        assertEquals("Dumbbells 15 lb", EquipmentLoads.describe(mixed))
    }
}
