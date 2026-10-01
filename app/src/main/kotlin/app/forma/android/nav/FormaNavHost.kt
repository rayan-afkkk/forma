package app.forma.android.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.forma.presentation.Destination
import app.forma.presentation.Tab
import app.forma.ui.app.DestinationContent
import app.forma.ui.app.FormaShell
import app.forma.ui.app.LocalNavigator
import app.forma.ui.app.Navigator
import app.forma.ui.platform.PlatformBackHandler
import app.forma.ui.theme.LocalReduceMotion

/** Maps the shared [Navigator] onto Navigation Compose. */
class AndroidNavigator(private val nav: NavHostController, private val finish: () -> Unit) : Navigator {
    override fun navigate(destination: Destination, clearBackStack: Boolean, replaceCurrent: Boolean) {
        val route = destination.toRoute()
        when {
            // Switching tabs, finishing onboarding or resetting data: the destination becomes the root.
            clearBackStack || destination is Destination.Main -> nav.navigate(route) {
                popUpTo(nav.graph.id) { inclusive = true }
                launchSingleTop = true
            }
            // e.g. the player is replaced by the summary, so Back does not return to a finished workout.
            replaceCurrent -> {
                val current = nav.currentBackStackEntry?.destination?.id
                nav.navigate(route) { if (current != null) popUpTo(current) { inclusive = true } }
            }
            else -> nav.navigate(route) { launchSingleTop = true }
        }
    }

    override fun back() {
        if (nav.previousBackStackEntry != null) nav.popBackStack() else finish()
    }
}

private fun NavDestination.tab(): Tab? = when {
    hasRoute<NavToday>() -> Tab.TODAY
    hasRoute<NavExplore>() -> Tab.EXPLORE
    hasRoute<NavProgress>() -> Tab.PROGRESS
    hasRoute<NavYou>() -> Tab.YOU
    else -> null
}

@Composable
fun FormaNavHost(onboarded: Boolean, finish: () -> Unit) {
    val nav = rememberNavController()
    val navigator = remember(nav) { AndroidNavigator(nav, finish) }
    val entry by nav.currentBackStackEntryAsState()
    val currentTab = entry?.destination?.tab()
    val reduceMotion = LocalReduceMotion.current
    // Chosen once. Changing NavHost's start destination later would rebuild the graph and drop
    // the back stack (e.g. the first-workout preview shown right after onboarding).
    val start = remember { if (onboarded) NavToday else NavOnboarding }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        // Back from the root of Explore, Progress or You goes to Today before leaving the app.
        PlatformBackHandler(enabled = currentTab != null && currentTab != Tab.TODAY && nav.previousBackStackEntry == null) {
            navigator.navigate(Destination.Main(Tab.TODAY))
        }
        FormaShell(currentTab = currentTab) {
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) },
                exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(120)) },
                popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(tween(180)) },
                popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(tween(120)) },
            ) {
                composable<NavToday> { DestinationContent(Destination.Main(Tab.TODAY)) }
                composable<NavExplore> { DestinationContent(Destination.Main(Tab.EXPLORE)) }
                composable<NavProgress> { DestinationContent(Destination.Main(Tab.PROGRESS)) }
                composable<NavYou> { DestinationContent(Destination.Main(Tab.YOU)) }
                composable<NavOnboarding> { DestinationContent(Destination.Onboarding) }
                composable<NavPreview> { DestinationContent(Destination.Preview(it.toRoute<NavPreview>().planKey)) }
                composable<NavPlayer> { DestinationContent(Destination.Player(it.toRoute<NavPlayer>().sessionId)) }
                composable<NavSummary> {
                    val r = it.toRoute<NavSummary>()
                    DestinationContent(Destination.Summary(r.sessionId, r.justFinished))
                }
                composable<NavPaywall> { DestinationContent(Destination.Paywall(it.toRoute<NavPaywall>().reason)) }
                composable<NavEquipmentEditor> { DestinationContent(Destination.EquipmentEditor(it.toRoute<NavEquipmentEditor>().profileId)) }
                composable<NavPage> { DestinationContent(it.toRoute<NavPage>().toDestination()) }
            }
        }
    }
}
