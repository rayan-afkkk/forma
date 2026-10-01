package app.forma.android.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.forma.core.domain.AppClock
import app.forma.core.domain.BackupCodec
import app.forma.core.domain.BillingGateway
import app.forma.core.domain.BillingPeriod
import app.forma.core.domain.OffersState
import app.forma.core.domain.PlanOffer
import app.forma.core.domain.PurchaseResult
import app.forma.core.domain.RestoreResult
import app.forma.core.model.EntitlementSource
import app.forma.core.model.EntitlementState
import app.forma.core.model.Tier
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play Billing (PBL 9.1) behind the app's [BillingGateway]. UNVERIFIED in this
 * repository: it was written against the official integration guide but could not be compiled or
 * run here, and no Play Console product exists yet. See docs/BILLING.md.
 *
 * Products: one subscription [PRODUCT_ID] with base plans [BASE_PLAN_MONTHLY] and [BASE_PLAN_ANNUAL].
 * Prices and terms always come from Google Play product details, already localised.
 *
 * Without a backend, entitlement is checked on the device with queryPurchasesAsync, which only
 * returns active subscriptions. The last confirmed state is cached; if Play is unreachable, a
 * previously confirmed Pro entitlement is honoured for [OFFLINE_GRACE_MS] so offline training is
 * not disrupted. Server-side verification (Play Developer API + RTDN) is recommended before launch.
 */
class PlayBillingGateway(
    private val context: Context,
    private val scope: CoroutineScope,
    private val cache: EntitlementCache,
    private val clock: AppClock,
    private val currentActivity: () -> Activity?,
) : BillingGateway, PurchasesUpdatedListener {

    private val entitlementFlow = MutableStateFlow(EntitlementState.Free)
    override val entitlement: StateFlow<EntitlementState> = entitlementFlow.asStateFlow()
    private val offersFlow = MutableStateFlow<OffersState>(OffersState.Loading)
    override val offers: StateFlow<OffersState> = offersFlow.asStateFlow()
    override val isDevelopment: Boolean = false

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    @Volatile private var productDetails: ProductDetails? = null
    @Volatile private var inFlight: CompletableDeferred<PurchaseResult>? = null

    init {
        scope.launch {
            cache.read()?.let { entitlementFlow.value = withOfflineGrace(it) }
            refresh()
        }
    }

    private suspend fun connected(): Boolean {
        if (client.isReady) return true
        return suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (cont.isActive) cont.resume(billingResult.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    // Automatic service reconnection is enabled; nothing to do here.
                }
            })
        }
    }

    override suspend fun refresh() {
        try {
            if (!connected()) {
                entitlementFlow.value = withOfflineGrace(entitlementFlow.value)
                return
            }
            val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
            val result = client.queryPurchasesAsync(params)
            if (result.billingResult.responseCode == BillingResponseCode.OK) {
                apply(result.purchasesList)
            } else {
                entitlementFlow.value = withOfflineGrace(entitlementFlow.value)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Purchase refresh failed", e)
            entitlementFlow.value = withOfflineGrace(entitlementFlow.value)
        }
    }

    /** Applies the store's current purchases and acknowledges any that need it. */
    private suspend fun apply(purchases: List<Purchase>) {
        val pro = purchases.filter { PRODUCT_ID in it.products }
        val active = pro.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val pending = pro.any { it.purchaseState == Purchase.PurchaseState.PENDING }
        if (active != null && !active.isAcknowledged) {
            val ack = client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(active.purchaseToken).build(),
            )
            if (ack.responseCode != BillingResponseCode.OK) Log.w(TAG, "Acknowledgement failed: ${ack.debugMessage}")
        }
        val state = if (active != null) {
            EntitlementState(
                tier = Tier.PRO,
                source = EntitlementSource.PLAY,
                productId = PRODUCT_ID,
                purchaseTime = active.purchaseTime,
                autoRenewing = active.isAutoRenewing,
                pendingPurchase = false,
                verifiedAt = clock.nowMillis(),
            )
        } else {
            EntitlementState(pendingPurchase = pending, verifiedAt = clock.nowMillis())
        }
        entitlementFlow.value = state
        cache.write(state)
    }

    private fun withOfflineGrace(state: EntitlementState): EntitlementState {
        val verified = state.verifiedAt ?: return EntitlementState.Free
        return if (state.isPro && clock.nowMillis() - verified <= OFFLINE_GRACE_MS) state else state.copy(tier = Tier.FREE, source = EntitlementSource.NONE)
    }

    override suspend fun loadOffers() {
        if (!connected()) {
            offersFlow.value = OffersState.Unavailable("Google Play isn't available right now, so prices can't be shown. Try again later.")
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                ),
            )
            .build()
        val (billingResult, list) = suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { result, queryResult ->
                if (cont.isActive) cont.resume(result to queryResult.productDetailsList)
            }
        }
        val details = list.firstOrNull { it.productId == PRODUCT_ID }
        if (billingResult.responseCode != BillingResponseCode.OK || details == null) {
            offersFlow.value = OffersState.Unavailable("Pro isn't available to buy on this device right now.")
            return
        }
        productDetails = details
        val offers = details.subscriptionOfferDetails.orEmpty()
            // Base plans only (no promotional offers), so the price shown is the price charged.
            .filter { it.offerId == null && it.basePlanId in setOf(BASE_PLAN_MONTHLY, BASE_PLAN_ANNUAL) }
            .mapNotNull { offer ->
                val phase = offer.pricingPhases.pricingPhaseList.lastOrNull() ?: return@mapNotNull null
                val annual = offer.basePlanId == BASE_PLAN_ANNUAL
                PlanOffer(
                    id = offer.basePlanId,
                    period = if (annual) BillingPeriod.ANNUAL else BillingPeriod.MONTHLY,
                    title = if (annual) "Annual" else "Monthly",
                    formattedPrice = phase.formattedPrice,
                    priceMicros = phase.priceAmountMicros,
                    currencyCode = phase.priceCurrencyCode,
                    renewalTerms = "Billed ${phase.formattedPrice} ${if (annual) "once a year" else "every month"}. " +
                        "Renews automatically until you cancel.",
                )
            }
            .sortedBy { if (it.period == BillingPeriod.ANNUAL) 0 else 1 }
        offersFlow.value = if (offers.isEmpty()) OffersState.Unavailable("Pro isn't available to buy on this device right now.")
        else OffersState.Available(offers)
    }

    override suspend fun purchase(offerId: String): PurchaseResult {
        if (inFlight?.isActive == true) return PurchaseResult.Failed("A purchase is already in progress.")
        if (productDetails == null) loadOffers()
        val details = productDetails ?: return PurchaseResult.Failed("Prices couldn't be loaded from Google Play. You haven't been charged.")
        val offer = details.subscriptionOfferDetails.orEmpty().firstOrNull { it.basePlanId == offerId && it.offerId == null }
            ?: return PurchaseResult.Failed("That plan isn't available. You haven't been charged.")
        val activity = currentActivity() ?: return PurchaseResult.Failed("Couldn't open Google Play. Please try again.")
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        val deferred = CompletableDeferred<PurchaseResult>()
        inFlight = deferred
        val launch = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, flowParams) }
        if (launch.responseCode != BillingResponseCode.OK) {
            inFlight = null
            return errorResult(launch)
        }
        return deferred.await()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        val deferred = inFlight
        scope.launch {
            val result = when (billingResult.responseCode) {
                BillingResponseCode.OK -> {
                    apply(purchases.orEmpty())
                    refresh()
                    val state = entitlementFlow.value
                    when {
                        state.isPro -> PurchaseResult.Purchased
                        state.pendingPurchase -> PurchaseResult.Pending
                        else -> PurchaseResult.Failed("The purchase didn't complete. You haven't been charged.")
                    }
                }
                BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
                BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    refresh()
                    PurchaseResult.AlreadyOwned
                }
                else -> errorResult(billingResult)
            }
            deferred?.complete(result)
            if (deferred === inFlight) inFlight = null
        }
    }

    private fun errorResult(result: BillingResult): PurchaseResult.Failed {
        Log.w(TAG, "Billing error ${result.responseCode}: ${result.debugMessage}")
        return PurchaseResult.Failed(
            when (result.responseCode) {
                BillingResponseCode.SERVICE_UNAVAILABLE, BillingResponseCode.NETWORK_ERROR, BillingResponseCode.SERVICE_DISCONNECTED ->
                    "Google Play couldn't be reached. Check your connection and try again. You haven't been charged."
                BillingResponseCode.BILLING_UNAVAILABLE -> "Purchases aren't available on this device or account."
                BillingResponseCode.ITEM_UNAVAILABLE -> "This plan isn't available in your country or on this account."
                BillingResponseCode.DEVELOPER_ERROR -> "Purchases aren't set up correctly in this build."
                else -> "The purchase couldn't be completed. You haven't been charged."
            },
        )
    }

    override suspend fun restore(): RestoreResult {
        if (!connected()) return RestoreResult.Failed("Google Play couldn't be reached. Try again later.")
        refresh()
        return if (entitlementFlow.value.isPro) RestoreResult.Restored else RestoreResult.NothingToRestore
    }

    override fun manageSubscriptionUrl(): String =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=${context.packageName}"

    companion object {
        const val PRODUCT_ID = "forma_pro"
        const val BASE_PLAN_MONTHLY = "monthly"
        const val BASE_PLAN_ANNUAL = "annual"
        const val OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000
        private const val TAG = "FormaBilling"
    }
}

/** Last confirmed entitlement, so an offline start does not lock a paying person out. */
class EntitlementCache(private val store: DataStore<Preferences>) {
    private val key = stringPreferencesKey("entitlement_v1")

    suspend fun read(): EntitlementState? = store.data.first()[key]?.let {
        runCatching { BackupCodec.json.decodeFromString(EntitlementState.serializer(), it) }.getOrNull()
    }

    suspend fun write(state: EntitlementState) {
        store.edit { it[key] = BackupCodec.json.encodeToString(EntitlementState.serializer(), state) }
    }
}
