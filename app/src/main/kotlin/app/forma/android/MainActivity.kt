package app.forma.android

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.forma.android.nav.FormaNavHost
import app.forma.android.ui.AndroidFonts
import app.forma.core.model.ThemeMode
import app.forma.presentation.AppServices
import app.forma.presentation.root.AppStateHolder
import app.forma.ui.app.LocalAppServices
import app.forma.ui.app.LocalSnackbar
import app.forma.ui.platform.collectUiState
import app.forma.ui.platform.rememberReduceMotion
import app.forma.ui.platform.rememberStateHolder
import app.forma.ui.theme.Forma
import app.forma.ui.theme.FormaTheme
import app.forma.ui.theme.LocalCompactLayout
import app.forma.ui.theme.LocalReduceMotion
import app.forma.ui.theme.LocalScreenPadding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val services: AppServices get() = (application as FormaApplication).container.services

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        applySystemBars(night)
        setContent {
            CompositionLocalProvider(LocalAppServices provides services) {
                FormaRoot(onDarkChanged = ::applySystemBars, finish = ::finish)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up purchases made elsewhere, renewals, expiry and refunds. Never interrupts a
        // workout: entitlement only changes what can be started next.
        lifecycleScope.launch { services.billing.refresh() }
    }

    private var lastDark: Boolean? = null

    private fun applySystemBars(dark: Boolean) {
        if (lastDark == dark) return
        lastDark = dark
        val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
}

@Composable
private fun FormaRoot(onDarkChanged: (Boolean) -> Unit, finish: () -> Unit) {
    val services = LocalAppServices.current
    val app = rememberStateHolder("app") { AppStateHolder(it, services) }
    val state by app.state.collectUiState()
    val dark = when (state.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    LaunchedEffect(dark) { onDarkChanged(dark) }
    val snackbar = remember { SnackbarHostState() }
    val reduceMotion = rememberReduceMotion()

    FormaTheme(darkTheme = dark, fonts = AndroidFonts) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Forma.colors.background)) {
            val fontScale = LocalDensity.current.fontScale
            CompositionLocalProvider(
                LocalScreenPadding provides if (maxWidth < 380.dp || fontScale >= 1.75f) 16.dp else 20.dp,
                LocalCompactLayout provides (maxWidth < 380.dp || fontScale >= 1.5f),
                LocalReduceMotion provides reduceMotion,
                LocalSnackbar provides snackbar,
            ) {
                // Wait for stored state so the first screen is the right one (no onboarding flash).
                if (!state.loading) {
                    FormaNavHost(onboarded = state.onboarded, finish = finish)
                } else {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }
}
