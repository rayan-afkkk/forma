package app.forma.presentation

import app.forma.core.domain.AppClock
import app.forma.core.domain.BackupService
import app.forma.core.domain.BillingGateway
import app.forma.core.domain.DataResetService
import app.forma.core.domain.IdGenerator
import app.forma.core.domain.OnboardingService
import app.forma.core.domain.PlanningService
import app.forma.core.domain.ProductEvents
import app.forma.core.domain.Repositories
import app.forma.core.domain.SessionService
import app.forma.core.engine.ContentCatalog
import app.forma.core.model.ReminderSettings
import app.forma.core.model.Schedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Schedules workout reminders. WorkManager on Android; a no-op elsewhere. */
fun interface ReminderScheduler {
    fun update(reminders: ReminderSettings, schedule: Schedule)

    companion object {
        val None = ReminderScheduler { _, _ -> }
    }
}

data class AppInfo(
    val versionName: String,
    /** Debug builds show developer options such as the Free/Pro switch. */
    val isDebugBuild: Boolean,
    /** Contact address for help and feedback. Empty until the publisher provides one. */
    val supportEmail: String = "",
)

/** Everything screens need, created once per process by the platform. */
class AppServices(
    val repos: Repositories,
    val clock: AppClock,
    val billing: BillingGateway,
    val reminders: ReminderScheduler = ReminderScheduler.None,
    val info: AppInfo = AppInfo("dev", isDebugBuild = true),
    val catalog: ContentCatalog = ContentCatalog.Default,
    val events: ProductEvents = ProductEvents.None,
    ids: IdGenerator = IdGenerator.Random,
) {
    val planning = PlanningService(repos, billing, clock, catalog, events)
    val sessions = SessionService(repos, planning, clock, ids, events)
    val onboarding = OnboardingService(repos, planning, clock, events)
    val backup = BackupService(repos, clock)
    val reset = DataResetService(repos)
}

/** One-off things a screen asks the platform to do. */
sealed interface Effect {
    /**
     * [clearBackStack] resets navigation to [destination]; [replaceCurrent] removes the current
     * screen first (e.g. the player is replaced by the summary so Back does not return to it).
     */
    data class Navigate(
        val destination: Destination,
        val clearBackStack: Boolean = false,
        val replaceCurrent: Boolean = false,
    ) : Effect
    data object Back : Effect
    data class Message(val text: String) : Effect
    data class OpenUrl(val url: String) : Effect

    /** Ask the platform to save [content] through the system file picker. */
    data class SaveFile(val suggestedName: String, val mimeType: String, val content: String) : Effect

    /** Ask the platform to let the person pick a file to import. */
    data object PickImportFile : Effect

    /** Play the rest-finished cue (sound and/or haptic, per settings). */
    data class Cue(val sound: Boolean, val haptic: Boolean) : Effect
}

/**
 * Screen logic without any UI or Android dependency. The Android app wraps each holder in a
 * ViewModel; the JVM verification harness uses them directly. State is a single observable value.
 */
abstract class StateHolder<S : Any>(protected val scope: CoroutineScope, initial: S) {
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<S> = mutable.asStateFlow()
    protected val current: S get() = mutable.value

    private val effectChannel = Channel<Effect>(Channel.BUFFERED)
    val effects: Flow<Effect> = effectChannel.receiveAsFlow()

    protected fun update(transform: (S) -> S) = mutable.update(transform)
    protected fun set(value: S) {
        mutable.value = value
    }
    protected fun emit(effect: Effect) {
        effectChannel.trySend(effect)
    }
    protected fun navigate(destination: Destination, clear: Boolean = false) = emit(Effect.Navigate(destination, clear))
    protected fun message(text: String) = emit(Effect.Message(text))
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)
}

enum class Tab(val label: String) { TODAY("Today"), EXPLORE("Explore"), PROGRESS("Progress"), YOU("You") }

sealed interface Destination {
    data class Main(val tab: Tab) : Destination
    data object Onboarding : Destination
    data class Preview(val planKey: String) : Destination
    data class Player(val sessionId: String) : Destination
    data class Summary(val sessionId: String, val justFinished: Boolean) : Destination
    data class Paywall(val reason: String) : Destination
    data object Goals : Destination
    data object Schedule : Destination
    data object Equipment : Destination
    data class EquipmentEditor(val profileId: String?) : Destination
    data object Exclusions : Destination
    data object SoundAndHaptics : Destination
    data object Membership : Destination
    data object Backup : Destination
    data object Help : Destination
    data object Privacy : Destination
}
