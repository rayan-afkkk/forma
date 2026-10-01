package app.forma.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.presentation.Effect
import app.forma.presentation.StateHolder
import app.forma.presentation.Tab
import app.forma.ui.components.FormaBottomBar
import app.forma.ui.components.FormaSnackbarHost
import app.forma.ui.platform.rememberCuePlayer
import app.forma.ui.screens.explore.ExploreRoute
import app.forma.ui.screens.onboarding.OnboardingRoute
import app.forma.ui.screens.player.PlayerRoute
import app.forma.ui.screens.preview.PreviewRoute
import app.forma.ui.screens.progress.ProgressRoute
import app.forma.ui.screens.summary.SummaryRoute
import app.forma.ui.screens.today.TodayRoute
import app.forma.ui.screens.you.BackupRoute
import app.forma.ui.screens.you.EquipmentEditorRoute
import app.forma.ui.screens.you.EquipmentListRoute
import app.forma.ui.screens.you.ExclusionsRoute
import app.forma.ui.screens.you.GoalsRoute
import app.forma.ui.screens.you.HelpScreen
import app.forma.ui.screens.you.MembershipRoute
import app.forma.ui.screens.you.PrivacyScreen
import app.forma.ui.screens.you.ScheduleRoute
import app.forma.ui.screens.you.SoundAndHapticsRoute
import app.forma.ui.screens.you.YouRoute
import app.forma.ui.theme.Forma
import kotlinx.coroutines.launch

/** Navigation as the shared UI sees it. Android maps this onto Navigation Compose. */
interface Navigator {
    fun navigate(destination: Destination, clearBackStack: Boolean = false, replaceCurrent: Boolean = false)
    fun back()
}

val LocalAppServices = staticCompositionLocalOf<AppServices> { error("AppServices not provided") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator not provided") }
val LocalSnackbar = staticCompositionLocalOf<SnackbarHostState> { error("Snackbar host not provided") }

/**
 * Whether on-screen timers tick in real time. Always true in the app; UI tests turn it off so the
 * test framework can reach an idle state (timers are still derived from timestamps on every action).
 */
val LocalLiveTimers = staticCompositionLocalOf { true }

/** Renders the screen for any destination. Both the Android NavHost and the JVM harness call this. */
@Composable
fun DestinationContent(destination: Destination) {
    when (destination) {
        is Destination.Main -> when (destination.tab) {
            Tab.TODAY -> TodayRoute()
            Tab.EXPLORE -> ExploreRoute()
            Tab.PROGRESS -> ProgressRoute()
            Tab.YOU -> YouRoute()
        }
        Destination.Onboarding -> OnboardingRoute()
        is Destination.Preview -> PreviewRoute(destination.planKey)
        is Destination.Player -> PlayerRoute(destination.sessionId)
        is Destination.Summary -> SummaryRoute(destination.sessionId, destination.justFinished)
        is Destination.Paywall -> MembershipRoute(paywall = true)
        Destination.Membership -> MembershipRoute(paywall = false)
        Destination.Goals -> GoalsRoute()
        Destination.Schedule -> ScheduleRoute()
        Destination.Equipment -> EquipmentListRoute()
        is Destination.EquipmentEditor -> EquipmentEditorRoute(destination.profileId)
        Destination.Exclusions -> ExclusionsRoute()
        Destination.SoundAndHaptics -> SoundAndHapticsRoute()
        Destination.Backup -> BackupRoute()
        Destination.Help -> HelpScreen()
        Destination.Privacy -> PrivacyScreen()
    }
}

/** The app frame: content, the bottom bar on main tabs (hidden during workouts), and messages. */
@Composable
fun FormaShell(currentTab: Tab?, content: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    Box(Modifier.fillMaxSize().background(Forma.colors.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            if (currentTab != null) {
                FormaBottomBar(currentTab, onSelect = { navigator.navigate(Destination.Main(it)) })
            }
        }
        FormaSnackbarHost(
            LocalSnackbar.current,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (currentTab != null) 76.dp else 12.dp),
        )
    }
}

/** Routes a holder's one-off effects to navigation, messages, links and cues. */
@Composable
fun HandleEffects(
    holder: StateHolder<*>,
    onSaveFile: ((Effect.SaveFile) -> Unit)? = null,
    onPickImport: (() -> Unit)? = null,
) {
    val navigator = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val uriHandler = LocalUriHandler.current
    val cues = rememberCuePlayer()
    val scope = rememberCoroutineScope()
    LaunchedEffect(holder) {
        holder.effects.collect { effect ->
            when (effect) {
                is Effect.Navigate -> navigator.navigate(effect.destination, effect.clearBackStack, effect.replaceCurrent)
                Effect.Back -> navigator.back()
                is Effect.Message -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(effect.text)
                }
                is Effect.OpenUrl -> runCatching { uriHandler.openUri(effect.url) }
                is Effect.Cue -> cues.play(effect.sound, effect.haptic)
                is Effect.SaveFile -> onSaveFile?.invoke(effect)
                Effect.PickImportFile -> onPickImport?.invoke()
            }
        }
    }
}
