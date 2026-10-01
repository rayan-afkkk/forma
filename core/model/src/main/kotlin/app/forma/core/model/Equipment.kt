package app.forma.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One piece of equipment a person owns. New equipment types (kettlebells, bands, a pull-up bar)
 * are added as new subtypes; exercises declare what they need via [EquipmentRequirement].
 */
@Serializable
sealed interface EquipmentItem {

    /** Fixed-weight dumbbells at one weight. [quantity] 2 means a matching pair. */
    @Serializable
    @SerialName("fixed_dumbbells")
    data class FixedDumbbells(val weightHundredths: Int, val quantity: Int) : EquipmentItem {
        init {
            require(weightHundredths > 0) { "Dumbbell weight must be positive" }
            require(quantity in 1..2) { "Quantity must be 1 or 2" }
        }
    }

    /** Adjustable dumbbell handles. Every weight from [minHundredths] to [maxHundredths] in steps of [incrementHundredths]. */
    @Serializable
    @SerialName("adjustable_dumbbells")
    data class AdjustableDumbbells(
        val minHundredths: Int,
        val maxHundredths: Int,
        val incrementHundredths: Int,
        val quantity: Int,
    ) : EquipmentItem {
        init {
            require(minHundredths > 0) { "Minimum must be positive" }
            require(maxHundredths >= minHundredths) { "Maximum must be at least the minimum" }
            require(incrementHundredths > 0) { "Increment must be positive" }
            require(quantity in 1..2) { "Quantity must be 1 or 2" }
        }
    }

    /** A flat, stable bench. */
    @Serializable
    @SerialName("bench")
    data object Bench : EquipmentItem
}

/**
 * A named set of equipment, e.g. "Home" or "Travel". All weights in a profile use [unit], so a
 * prescription is always expressed in weights the person actually owns.
 */
@Serializable
data class EquipmentProfile(
    val id: String,
    val name: String,
    val unit: WeightUnit,
    val items: List<EquipmentItem>,
) {
    val hasBench: Boolean get() = items.any { it is EquipmentItem.Bench }
    val hasDumbbells: Boolean
        get() = items.any { it is EquipmentItem.FixedDumbbells || it is EquipmentItem.AdjustableDumbbells }
    val isBodyweightOnly: Boolean get() = items.isEmpty()

    companion object {
        fun bodyweight(id: String, unit: WeightUnit = WeightUnit.KG) =
            EquipmentProfile(id = id, name = "Home", unit = unit, items = emptyList())
    }
}

/** How an exercise uses dumbbells. */
@Serializable
enum class DumbbellUse {
    /** Two dumbbells of the same weight, one in each hand. */
    PAIR,

    /** One dumbbell in one hand (unilateral work). */
    SINGLE,

    /** One dumbbell held with both hands. */
    GOBLET,
}

@Serializable
data class EquipmentRequirement(
    val dumbbells: DumbbellUse? = null,
    val bench: BenchNeed = BenchNeed.NONE,
) {
    val isBodyweight: Boolean get() = dumbbells == null && bench == BenchNeed.NONE

    companion object {
        val None = EquipmentRequirement()
    }
}

@Serializable
enum class BenchNeed { NONE, REQUIRED }
