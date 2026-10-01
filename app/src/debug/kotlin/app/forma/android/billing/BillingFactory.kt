package app.forma.android.billing

import android.app.Activity
import android.content.Context
import app.forma.BuildConfig
import app.forma.core.domain.AppClock
import app.forma.core.domain.BillingGateway
import app.forma.core.domain.DevelopmentBilling
import app.forma.core.model.Tier
import kotlinx.coroutines.CoroutineScope

/**
 * Debug builds use the development entitlement provider (a local Free/Pro switch in You ›
 * Developer options) unless USE_PLAY_BILLING is enabled for testing with license testers.
 * This file exists only in the debug source set, so release builds cannot use it.
 */
object BillingFactory {
    fun create(
        context: Context,
        scope: CoroutineScope,
        cache: EntitlementCache,
        clock: AppClock,
        currentActivity: () -> Activity?,
    ): BillingGateway =
        if (BuildConfig.USE_PLAY_BILLING) PlayBillingGateway(context, scope, cache, clock, currentActivity)
        else DevelopmentBilling(Tier.FREE, clock)
}
