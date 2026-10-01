package app.forma.core.domain

import app.forma.core.engine.TrainingRules
import app.forma.core.model.Access
import app.forma.core.model.EntitlementState
import app.forma.core.model.ProFeature

/**
 * What the person can use right now. Free users keep everything they already have, including all
 * history, exclusions, logging and export. Only future Pro planning is gated, and an in-progress
 * workout is never interrupted when an entitlement changes.
 */
data class AccessState(
    val entitlement: EntitlementState,
    val sampleSessionsUsed: Int,
) {
    val isPro: Boolean get() = entitlement.isPro

    val sampleRemaining: Int
        get() = (TrainingRules.FREE_ADAPTIVE_SAMPLE_SESSIONS - sampleSessionsUsed).coerceAtLeast(0)

    /** Automatic progression: Pro, or within the free sample of adaptive sessions. */
    val adaptive: Boolean get() = isPro || sampleRemaining > 0

    fun can(feature: ProFeature): Boolean = when (feature) {
        ProFeature.ADAPTIVE_PROGRESSION -> adaptive
        else -> isPro
    }

    fun canUse(access: Access): Boolean = access == Access.FREE || isPro

    companion object {
        val Free = AccessState(EntitlementState.Free, 0)
    }
}
