package app.forma.harness

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import app.forma.core.domain.DevelopmentBilling
import app.forma.core.domain.FakeClock
import app.forma.core.domain.IdGenerator
import app.forma.core.domain.Repositories
import app.forma.core.model.Tier
import app.forma.presentation.AppInfo
import app.forma.presentation.AppServices
import app.forma.presentation.Destination
import app.forma.ui.app.DestinationContent
import app.forma.ui.app.FormaShell
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalNavigator
import app.forma.ui.app.LocalSnackbar
import app.forma.ui.app.Navigator

/** A minimal back stack standing in for Navigation Compose on the JVM. */
class HarnessNavigator(initial: Destination) : Navigator {
    val stack = mutableStateListOf(initial)
    val current: Destination get() = stack.last()

    override fun navigate(destination: Destination, clearBackStack: Boolean, replaceCurrent: Boolean) {
        when {
            clearBackStack -> {
                stack.clear()
                stack.add(destination)
            }
            destination is Destination.Main -> {
                // Switching tabs returns to that tab's root, as the Android bottom bar does.
                stack.clear()
                stack.add(destination)
            }
            replaceCurrent -> {
                stack.removeAt(stack.lastIndex)
                stack.add(destination)
            }
            else -> stack.add(destination)
        }
    }

    override fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

class HarnessEnv(
    val clock: FakeClock = FakeClock(),
    val billing: DevelopmentBilling = DevelopmentBilling(Tier.FREE, clock),
) {
    private var n = 0
    val services = AppServices(
        repos = Repositories.inMemory(),
        clock = clock,
        billing = billing,
        info = AppInfo("0.1.0-dev (harness)", isDebugBuild = true),
        ids = IdGenerator { "session-${++n}" },
    )
}

@Composable
fun HarnessApp(env: HarnessEnv, navigator: HarnessNavigator) {
    val snackbar = remember { SnackbarHostState() }
    CompositionLocalProvider(
        LocalAppServices provides env.services,
        LocalNavigator provides navigator,
        LocalSnackbar provides snackbar,
    ) {
        val destination = navigator.current
        FormaShell(currentTab = (destination as? Destination.Main)?.tab) {
            key(destination) { DestinationContent(destination) }
        }
    }
}
