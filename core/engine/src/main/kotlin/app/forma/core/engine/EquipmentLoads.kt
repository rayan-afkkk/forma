package app.forma.core.engine

import app.forma.core.model.DumbbellUse
import app.forma.core.model.EquipmentItem
import app.forma.core.model.EquipmentProfile
import app.forma.core.model.Load
import app.forma.core.model.WeightUnit
import app.forma.core.model.formatAmount

/** Works out which loads a person can actually use. Never invents a weight they do not own. */
object EquipmentLoads {

    /**
     * Every load available for [use], ascending, in the profile's unit.
     * A pair requires two dumbbells of the same weight; mixing two different single dumbbells is not a pair.
     */
    fun available(profile: EquipmentProfile, use: DumbbellUse): List<Load> {
        val needed = if (use == DumbbellUse.PAIR) 2 else 1
        val hundredths = sortedSetOf<Int>()
        for (item in profile.items) {
            when (item) {
                is EquipmentItem.FixedDumbbells -> if (item.quantity >= needed) hundredths += item.weightHundredths
                is EquipmentItem.AdjustableDumbbells -> if (item.quantity >= needed) {
                    var w = item.minHundredths
                    while (w <= item.maxHundredths) {
                        hundredths += w
                        w += item.incrementHundredths
                    }
                }
                EquipmentItem.Bench -> Unit
            }
        }
        // Fixed and adjustable sets can both be present; merge by weight.
        return hundredths.map { Load(it, profile.unit) }
    }

    /** The heaviest available load not above [desired] (compared by mass); the lightest if all are heavier. */
    fun snap(available: List<Load>, desired: Load): Load? {
        if (available.isEmpty()) return null
        return available.lastOrNull { it.grams <= desired.grams + EPSILON_GRAMS } ?: available.first()
    }

    fun nextUp(available: List<Load>, current: Load): Load? =
        available.firstOrNull { it.grams > current.grams + EPSILON_GRAMS }

    fun nextDown(available: List<Load>, current: Load): Load? =
        available.lastOrNull { it.grams < current.grams - EPSILON_GRAMS }

    /** Whether moving from [from] to [to] is a small enough jump to prescribe. Draft rule. */
    fun isReasonableJump(from: Load, to: Load): Boolean {
        val deltaGrams = to.grams - from.grams
        if (deltaGrams <= 0) return true
        val absoluteLimitGrams = when (to.unit) {
            WeightUnit.KG -> TrainingRules.MAX_LOAD_JUMP_KG * 1000
            WeightUnit.LB -> TrainingRules.MAX_LOAD_JUMP_LB * Load.GRAMS_PER_POUND
        }
        return deltaGrams <= absoluteLimitGrams + EPSILON_GRAMS ||
            deltaGrams <= from.grams * TrainingRules.MAX_LOAD_JUMP_FRACTION + EPSILON_GRAMS
    }

    /** Short description such as "Dumbbells 2–20 kg (pair) · Bench" or "Bodyweight only". */
    fun describe(profile: EquipmentProfile): String {
        if (profile.isBodyweightOnly) return "Bodyweight only"
        val parts = mutableListOf<String>()
        val unit = profile.unit.symbol
        val pairs = available(profile, DumbbellUse.PAIR)
        val singles = available(profile, DumbbellUse.SINGLE)
        when {
            pairs.isNotEmpty() -> parts += "Dumbbells ${range(pairs)} $unit"
            singles.isNotEmpty() -> parts += "Single dumbbell ${range(singles)} $unit"
        }
        if (profile.hasBench) parts += "Bench"
        return parts.joinToString(" · ").ifEmpty { "Bodyweight only" }
    }

    private fun range(loads: List<Load>): String =
        if (loads.size == 1) formatAmount(loads.first().amount)
        else "${formatAmount(loads.first().amount)}–${formatAmount(loads.last().amount)}"

    private const val EPSILON_GRAMS = 0.5
}
