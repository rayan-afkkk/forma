package app.forma.presentation.you

import app.forma.core.domain.ImportResult
import app.forma.core.domain.OffersState
import app.forma.core.domain.PlanOffer
import app.forma.core.domain.ProductEvent
import app.forma.core.domain.PurchaseResult
import app.forma.core.domain.RestoreResult
import app.forma.core.model.ProFeature
import app.forma.presentation.AppServices
import app.forma.presentation.Effect
import app.forma.presentation.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

data class BenefitUi(val title: String, val detail: String)

data class OfferUi(
    val id: String,
    val title: String,
    val price: String,
    val terms: String,
    val placeholder: Boolean,
)

data class MembershipUiState(
    val loading: Boolean = true,
    val isPro: Boolean = false,
    val pending: Boolean = false,
    val statusText: String = "",
    val benefits: List<BenefitUi> = Benefits,
    val freeIncludes: List<String> = FreeIncludes,
    val offers: List<OfferUi> = emptyList(),
    val selectedOfferId: String? = null,
    val unavailable: String? = null,
    val purchasing: Boolean = false,
    val developmentNotice: String? = null,
) {
    companion object {
        val Benefits = listOf(
            BenefitUi(ProFeature.ADAPTIVE_PROGRESSION.label, "Each session adjusts to what you logged last time and how it felt."),
            BenefitUi(ProFeature.PRO_PROGRAMS.label, "Dumbbell Strength and Steady Habit, plus Pro sessions in Explore."),
            BenefitUi(ProFeature.MULTIPLE_EQUIPMENT_PROFILES.label, "Keep separate setups, such as Home and Travel."),
            BenefitUi(ProFeature.ADVANCED_CUSTOMIZATION.label, "Switch equipment for a day and fine-tune sessions."),
            BenefitUi(ProFeature.ADVANCED_PROGRESS.label, "Charts of each exercise over time."),
        )
        val FreeIncludes = listOf(
            "The complete Foundations program",
            "Demonstrations and technique cues",
            "Logging and your full workout history",
            "Permanent exercise exclusions",
            "Data export",
        )
    }
}

/**
 * The paywall and membership screen. Shows the full amount charged and renewal terms from the
 * store, can always be dismissed, and is never shown during a workout.
 */
class MembershipStateHolder(scope: CoroutineScope, private val services: AppServices) :
    StateHolder<MembershipUiState>(scope, MembershipUiState()) {

    init {
        services.events.track(ProductEvent.PAYWALL_VIEWED)
        launch { services.repos.userState.update { it.copy(upgradeOfferSeen = true) } }
        launch { services.billing.loadOffers() }
        launch {
            combine(services.billing.entitlement, services.billing.offers) { entitlement, offers -> entitlement to offers }
                .collect { (entitlement, offers) ->
                    val offerList = (offers as? OffersState.Available)?.offers.orEmpty()
                    update { s ->
                        s.copy(
                            loading = offers is OffersState.Loading,
                            isPro = entitlement.isPro,
                            pending = entitlement.pendingPurchase,
                            statusText = when {
                                entitlement.isPro && services.billing.isDevelopment -> "Pro is active through the development switch. This is not a purchase."
                                entitlement.isPro -> "Pro is active. Manage or cancel any time in Google Play."
                                entitlement.pendingPurchase -> "Your purchase is waiting for payment to complete. Pro unlocks as soon as it does."
                                else -> "You're on Free."
                            },
                            offers = offerList.map(::offerUi),
                            selectedOfferId = s.selectedOfferId?.takeIf { id -> offerList.any { it.id == id } } ?: offerList.firstOrNull()?.id,
                            unavailable = (offers as? OffersState.Unavailable)?.message,
                            developmentNotice = if (services.billing.isDevelopment) {
                                "Development build: purchases here only switch a local test flag. Prices shown are the proposed launch prices, not live store prices."
                            } else null,
                        )
                    }
                }
        }
    }

    private fun offerUi(offer: PlanOffer) = OfferUi(offer.id, offer.title, offer.formattedPrice, offer.renewalTerms, offer.isPlaceholderPrice)

    fun select(id: String) = update { it.copy(selectedOfferId = id) }

    fun purchase() {
        val id = current.selectedOfferId ?: return
        if (current.purchasing) return
        update { it.copy(purchasing = true) }
        launch {
            val result = try {
                services.billing.purchase(id)
            } catch (e: Exception) {
                PurchaseResult.Failed("Something went wrong. You haven't been charged.")
            }
            update { it.copy(purchasing = false) }
            when (result) {
                PurchaseResult.Purchased -> {
                    services.events.track(ProductEvent.PURCHASE_COMPLETED)
                    message("Welcome to Pro.")
                    emit(Effect.Back)
                }
                PurchaseResult.Pending -> message("Your purchase is pending. Pro unlocks once payment completes.")
                PurchaseResult.Cancelled -> Unit
                PurchaseResult.AlreadyOwned -> {
                    services.billing.refresh()
                    message("You already have Pro on this account.")
                }
                is PurchaseResult.Failed -> message(result.message)
            }
        }
    }

    fun restore() = launch {
        when (val result = runCatching { services.billing.restore() }.getOrElse { RestoreResult.Failed("Couldn't reach Google Play. Try again later.") }) {
            RestoreResult.Restored -> message("Your Pro membership is restored.")
            RestoreResult.NothingToRestore -> message("No active membership was found for this Google account.")
            is RestoreResult.Failed -> message(result.message)
        }
    }

    fun manage() = emit(Effect.OpenUrl(services.billing.manageSubscriptionUrl()))

    fun dismiss() = emit(Effect.Back)
}

// ------------------------------------------------------------------------------------------ backup

data class BackupUiState(
    val confirmImport: String? = null,
    val working: Boolean = false,
    val lastResult: String? = null,
)

class BackupStateHolder(scope: CoroutineScope, private val services: AppServices) : StateHolder<BackupUiState>(scope, BackupUiState()) {

    fun exportJson() = launch {
        val content = services.backup.export()
        emit(Effect.SaveFile("forma-backup-${LocalDate.now(services.clock.zone())}.json", "application/json", content))
    }

    fun exportCsv() = launch {
        val content = services.backup.exportSetsCsv()
        emit(Effect.SaveFile("forma-sets-${LocalDate.now(services.clock.zone())}.csv", "text/csv", content))
    }

    fun chooseImport() = emit(Effect.PickImportFile)

    /** Called by the platform with the picked file's contents. Asks before replacing anything. */
    fun fileRead(content: String) = update { it.copy(confirmImport = content) }

    fun cancelImport() = update { it.copy(confirmImport = null) }

    fun confirmImport() {
        val content = current.confirmImport ?: return
        update { it.copy(confirmImport = null, working = true) }
        launch {
            val text = when (val result = services.backup.import(content)) {
                is ImportResult.Imported -> "Imported ${result.sessions} ${if (result.sessions == 1) "session" else "sessions"} and your settings."
                is ImportResult.Invalid -> result.reason
            }
            val user = services.repos.userState.current()
            services.reminders.update(user.settings.reminders, user.schedule)
            update { it.copy(working = false, lastResult = text) }
            message(text)
        }
    }

    fun saved(success: Boolean) = message(if (success) "Saved." else "The file wasn't saved.")
    fun readFailed() = message("That file couldn't be opened.")
}
