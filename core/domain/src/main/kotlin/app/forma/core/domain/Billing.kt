package app.forma.core.domain

import app.forma.core.model.EntitlementSource
import app.forma.core.model.EntitlementState
import app.forma.core.model.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class BillingPeriod { MONTHLY, ANNUAL }

/** One purchasable plan. Prices come from the store and are already localised. */
data class PlanOffer(
    val id: String,
    val period: BillingPeriod,
    val title: String,
    /** Full amount charged each period, formatted by the store, e.g. "US$29.99". */
    val formattedPrice: String,
    val priceMicros: Long,
    val currencyCode: String,
    /** Plain renewal terms shown next to the price. */
    val renewalTerms: String,
    /** True for placeholder prices used when store data is unavailable in development builds. */
    val isPlaceholderPrice: Boolean = false,
)

sealed interface OffersState {
    data object Loading : OffersState
    data class Available(val offers: List<PlanOffer>) : OffersState

    /** The store is unavailable or not configured. [message] is shown to the person. */
    data class Unavailable(val message: String) : OffersState
}

sealed interface PurchaseResult {
    data object Purchased : PurchaseResult

    /** Payment is pending (for example, a cash payment). Pro unlocks once it completes. */
    data object Pending : PurchaseResult
    data object Cancelled : PurchaseResult
    data object AlreadyOwned : PurchaseResult
    data class Failed(val message: String) : PurchaseResult
}

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data object NothingToRestore : RestoreResult
    data class Failed(val message: String) : RestoreResult
}

/**
 * The app's view of subscriptions. On Android the production implementation wraps Google Play
 * Billing; debug builds can switch to [DevelopmentBilling]. UI and planning code only see this
 * interface, so a failed or cancelled purchase can never crash a screen.
 */
interface BillingGateway {
    val entitlement: StateFlow<EntitlementState>
    val offers: StateFlow<OffersState>

    suspend fun refresh()
    suspend fun loadOffers()
    suspend fun purchase(offerId: String): PurchaseResult
    suspend fun restore(): RestoreResult

    /** Store page where the person manages or cancels the subscription. */
    fun manageSubscriptionUrl(): String

    /** True when this gateway is a development stand-in that can never unlock real purchases. */
    val isDevelopment: Boolean
}

/**
 * Development-only entitlement provider for testing Free and Pro. It never talks to a store and is
 * not compiled into release builds of the Android app (see app/src/debug).
 */
class DevelopmentBilling(
    initialTier: Tier = Tier.FREE,
    private val clock: AppClock = SystemClock,
) : BillingGateway {
    private val state = MutableStateFlow(stateFor(initialTier))
    override val entitlement: StateFlow<EntitlementState> = state
    private val offersFlow = MutableStateFlow<OffersState>(OffersState.Loading)
    override val offers: StateFlow<OffersState> = offersFlow
    override val isDevelopment: Boolean = true

    /** Next purchase outcome, for testing failure and cancellation paths. */
    var nextPurchaseResult: PurchaseResult = PurchaseResult.Purchased

    fun setTier(tier: Tier) {
        state.value = stateFor(tier)
    }

    private fun stateFor(tier: Tier) = if (tier == Tier.PRO) {
        EntitlementState(tier = Tier.PRO, source = EntitlementSource.DEVELOPMENT, productId = "forma_pro", verifiedAt = clock.nowMillis())
    } else {
        EntitlementState.Free
    }

    override suspend fun refresh() = Unit

    override suspend fun loadOffers() {
        offersFlow.value = OffersState.Available(PlaceholderOffers)
    }

    override suspend fun purchase(offerId: String): PurchaseResult {
        val result = nextPurchaseResult
        if (result == PurchaseResult.Purchased) setTier(Tier.PRO)
        if (result == PurchaseResult.Pending) state.value = state.value.copy(pendingPurchase = true)
        return result
    }

    override suspend fun restore(): RestoreResult =
        if (state.value.isPro) RestoreResult.Restored else RestoreResult.NothingToRestore

    override fun manageSubscriptionUrl(): String = "https://play.google.com/store/account/subscriptions"

    companion object {
        /**
         * Proposed launch prices from the business plan, shown only when store prices cannot be
         * loaded in development. Production prices always come from Google Play product details.
         */
        val PlaceholderOffers = listOf(
            PlanOffer(
                id = "annual", period = BillingPeriod.ANNUAL, title = "Annual",
                formattedPrice = "US$29.99", priceMicros = 29_990_000, currencyCode = "USD",
                renewalTerms = "Billed US$29.99 once a year. Renews automatically until you cancel.",
                isPlaceholderPrice = true,
            ),
            PlanOffer(
                id = "monthly", period = BillingPeriod.MONTHLY, title = "Monthly",
                formattedPrice = "US$4.99", priceMicros = 4_990_000, currencyCode = "USD",
                renewalTerms = "Billed US$4.99 every month. Renews automatically until you cancel.",
                isPlaceholderPrice = true,
            ),
        )
    }
}
