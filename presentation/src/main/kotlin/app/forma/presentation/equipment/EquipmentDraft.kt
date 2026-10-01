package app.forma.presentation.equipment

import app.forma.core.engine.EquipmentLoads
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.WeightUnit
import app.forma.core.model.formatAmount
import kotlin.math.roundToInt

enum class DumbbellKind(val label: String) { ADJUSTABLE("Adjustable"), FIXED("Fixed weights") }

/**
 * Editable equipment form shared by onboarding and the equipment editor. Weights are typed as
 * text so partially entered values do not jump around; [toProfile] only succeeds when valid.
 */
data class EquipmentDraft(
    val unit: WeightUnit = WeightUnit.KG,
    val hasDumbbells: Boolean = false,
    val kind: DumbbellKind = DumbbellKind.ADJUSTABLE,
    /** Fixed weights in hundredths of [unit] -> quantity (2 = pair, 1 = single). */
    val fixed: Map<Int, Int> = emptyMap(),
    val adjustableMin: String = "2.5",
    val adjustableMax: String = "20",
    val adjustableIncrement: String = "2.5",
    val adjustablePair: Boolean = true,
    val bench: Boolean = false,
    val customWeight: String = "",
) {
    val commonWeights: List<Int>
        get() = when (unit) {
            WeightUnit.KG -> listOf(100, 200, 250, 400, 500, 600, 750, 800, 1000, 1200, 1250, 1500, 2000)
            WeightUnit.LB -> listOf(300, 500, 800, 1000, 1200, 1500, 2000, 2500, 3000, 3500, 4000, 5000)
        }

    val fixedList: List<Pair<Int, Int>> get() = fixed.entries.sortedBy { it.key }.map { it.key to it.value }

    fun label(hundredths: Int) = "${formatAmount(hundredths / 100.0)} ${unit.symbol}"

    fun errors(): List<String> {
        if (!hasDumbbells) return emptyList()
        return when (kind) {
            DumbbellKind.FIXED -> if (fixed.isEmpty()) listOf("Choose at least one weight, or switch to bodyweight only.") else emptyList()
            DumbbellKind.ADJUSTABLE -> {
                val min = parse(adjustableMin)
                val max = parse(adjustableMax)
                val inc = parse(adjustableIncrement)
                buildList {
                    if (min == null || min <= 0) add("Enter the lightest weight.")
                    if (max == null || max <= 0) add("Enter the heaviest weight.")
                    if (inc == null || inc <= 0) add("Enter how much the weight changes per step.")
                    if (min != null && max != null && max < min) add("The heaviest weight must be at least the lightest.")
                    if (min != null && max != null && inc != null && inc > 0 && (max - min) / inc > 200) {
                        add("That's more than 200 steps. Check the increment.")
                    }
                }
            }
        }
    }

    fun toProfile(id: String, name: String): EquipmentProfile? {
        if (errors().isNotEmpty()) return null
        val items = mutableListOf<EquipmentItem>()
        if (hasDumbbells) {
            when (kind) {
                DumbbellKind.FIXED -> fixed.forEach { (weight, qty) -> items += EquipmentItem.FixedDumbbells(weight, qty.coerceIn(1, 2)) }
                DumbbellKind.ADJUSTABLE -> items += EquipmentItem.AdjustableDumbbells(
                    minHundredths = parse(adjustableMin)!!,
                    maxHundredths = parse(adjustableMax)!!,
                    incrementHundredths = parse(adjustableIncrement)!!,
                    quantity = if (adjustablePair) 2 else 1,
                )
            }
        }
        if (bench) items += EquipmentItem.Bench
        return EquipmentProfile(id = id, name = name.ifBlank { "Home" }, unit = unit, items = items)
    }

    fun summary(): String = toProfile("draft", "Draft")?.let { EquipmentLoads.describe(it) } ?: "Incomplete"

    fun toggleFixed(hundredths: Int): EquipmentDraft =
        copy(fixed = if (hundredths in fixed) fixed - hundredths else fixed + (hundredths to 2))

    fun setQuantity(hundredths: Int, quantity: Int): EquipmentDraft = copy(fixed = fixed + (hundredths to quantity.coerceIn(1, 2)))

    fun addCustom(): EquipmentDraft {
        val value = parse(customWeight) ?: return this
        if (value <= 0 || value > 20_000) return this
        return copy(fixed = fixed + (value to 2), customWeight = "")
    }

    /** Switches the unit for entry. Existing typed values are kept as numbers, not converted. */
    fun withUnit(newUnit: WeightUnit): EquipmentDraft = if (newUnit == unit) this else when (newUnit) {
        WeightUnit.KG -> copy(unit = newUnit, fixed = emptyMap(), adjustableMin = "2.5", adjustableMax = "20", adjustableIncrement = "2.5")
        WeightUnit.LB -> copy(unit = newUnit, fixed = emptyMap(), adjustableMin = "5", adjustableMax = "50", adjustableIncrement = "5")
    }

    companion object {
        /** Parses "12.5" or "12,5" into hundredths. */
        fun parse(text: String): Int? {
            val cleaned = text.trim().replace(',', '.')
            if (cleaned.isEmpty()) return null
            val value = cleaned.toDoubleOrNull() ?: return null
            if (value.isNaN() || value < 0 || value > 1000) return null
            return (value * 100).roundToInt()
        }

        fun from(profile: EquipmentProfile): EquipmentDraft {
            var draft = EquipmentDraft(unit = profile.unit, bench = profile.hasBench, hasDumbbells = profile.hasDumbbells)
            val adjustable = profile.items.filterIsInstance<EquipmentItem.AdjustableDumbbells>().firstOrNull()
            val fixed = profile.items.filterIsInstance<EquipmentItem.FixedDumbbells>()
            draft = if (adjustable != null) {
                draft.copy(
                    kind = DumbbellKind.ADJUSTABLE,
                    adjustableMin = formatAmount(adjustable.minHundredths / 100.0),
                    adjustableMax = formatAmount(adjustable.maxHundredths / 100.0),
                    adjustableIncrement = formatAmount(adjustable.incrementHundredths / 100.0),
                    adjustablePair = adjustable.quantity >= 2,
                )
            } else {
                draft.copy(kind = DumbbellKind.FIXED, fixed = fixed.associate { it.weightHundredths to it.quantity })
            }
            return draft
        }
    }
}
