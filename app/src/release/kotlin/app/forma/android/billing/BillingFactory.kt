package app.forma.android.billing

import android.app.Activity
import android.content.Context
import app.forma.core.domain.AppClock
import app.forma.core.domain.BillingGateway
import kotlinx.coroutines.CoroutineScope

/** Release builds always use Google Play Billing. There is no development switch. */
object BillingFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        cache: EntitlementCache,
        clock: AppClock,
        currentActivity: () -> Activity?,
    ): BillingGateway = PlayBillingGateway(context, scope, cache, clock, currentActivity)
}
