package app.forma.core.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
enum class WeightUnit(val symbol: String) {
    KG("kg"),
    LB("lb"),
}

/**
 * A weight stored exactly as the person entered it, in hundredths of [unit].
 *
 * Loads are never rewritten when the display unit changes: a set logged at 10 kg stays 10 kg in
 * storage even if the person later switches the app to pounds. Conversion happens only for display
 * and for comparisons, never on the stored value.
 */
@Serializable
data class Load(val hundredths: Int, val unit: WeightUnit) : Comparable<Load> {
    init {
        require(hundredths >= 0) { "Load cannot be negative" }
    }

    val amount: Double get() = hundredths / 100.0

    val grams: Double
        get() = when (unit) {
            WeightUnit.KG -> hundredths * 10.0
            WeightUnit.LB -> hundredths / 100.0 * GRAMS_PER_POUND
        }

    /** Converted amount for display only. Never store the result. */
    fun amountIn(target: WeightUnit): Double = when {
        target == unit -> amount
        target == WeightUnit.KG -> grams / 1000.0
        else -> grams / GRAMS_PER_POUND
    }

    override fun compareTo(other: Load): Int = grams.compareTo(other.grams)

    companion object {
        const val GRAMS_PER_POUND = 453.59237

        fun of(amount: Double, unit: WeightUnit): Load = Load((amount * 100).roundToInt(), unit)
        fun kg(amount: Double): Load = of(amount, WeightUnit.KG)
        fun lb(amount: Double): Load = of(amount, WeightUnit.LB)
    }
}

/** Formats a stored amount without trailing zeros: 10.0 -> "10", 12.5 -> "12.5", 1.25 -> "1.25". */
fun formatAmount(value: Double): String {
    val hundredths = (value * 100).roundToInt()
    val whole = hundredths / 100
    val fraction = hundredths % 100
    return when {
        fraction == 0 -> whole.toString()
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

/**
 * Formats a load for display. When [displayUnit] differs from the stored unit the converted value
 * is rounded to one decimal and prefixed with "≈" so it is never mistaken for an exact stored value.
 */
fun Load.format(displayUnit: WeightUnit = unit): String {
    if (displayUnit == unit) return "${formatAmount(amount)} ${unit.symbol}"
    val converted = (amountIn(displayUnit) * 10).roundToInt() / 10.0
    return "≈${formatAmount(converted)} ${displayUnit.symbol}"
}
