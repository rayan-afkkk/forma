package app.forma.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Tier { FREE, PRO }

@Serializable
enum class EntitlementSource {
    NONE,

    /** Google Play purchase seen on this device. */
    PLAY,

    /** Debug-only override for testing. Never available in release builds. */
    DEVELOPMENT,
}

@Serializable
data class EntitlementState(
    val tier: Tier = Tier.FREE,
    val source: EntitlementSource = EntitlementSource.NONE,
    val productId: String? = null,
    val purchaseTime: Long? = null,
    val autoRenewing: Boolean? = null,
    /** A purchase is waiting for payment (e.g. cash payment). Not entitled until it completes. */
    val pendingPurchase: Boolean = false,
    /** Last time the store confirmed this state. Null if never confirmed. */
    val verifiedAt: Long? = null,
) {
    val isPro: Boolean get() = tier == Tier.PRO

    companion object {
        val Free = EntitlementState()
    }
}

enum class ProFeature(val label: String) {
    PRO_PROGRAMS("Additional programs"),
    ADAPTIVE_PROGRESSION("Ongoing adaptive planning and progression"),
    MULTIPLE_EQUIPMENT_PROFILES("Multiple equipment profiles"),
    ADVANCED_CUSTOMIZATION("Advanced session customization"),
    ADVANCED_PROGRESS("Exercise trends and additional progress analysis"),
}
