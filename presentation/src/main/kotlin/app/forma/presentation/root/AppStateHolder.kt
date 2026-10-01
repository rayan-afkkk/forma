package app.forma.presentation.root

import app.forma.core.model.AppSettings
import app.forma.core.model.SessionStatus
import app.forma.core.model.ThemeMode
import app.forma.presentation.AppServices
import app.forma.presentation.StateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine

data class AppUiState(
    val loading: Boolean = true,
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val settings: AppSettings = AppSettings(),
    val inProgressSessionId: String? = null,
)

/** App-wide state: whether onboarding is done, the theme and settings that affect every screen. */
class AppStateHolder(scope: CoroutineScope, private val services: AppServices) : StateHolder<AppUiState>(scope, AppUiState()) {
    init {
        launch {
            combine(services.repos.userState.state, services.repos.sessions.sessions) { user, sessions ->
                AppUiState(
                    loading = false,
                    onboarded = user.onboardingComplete,
                    theme = user.settings.theme,
                    settings = user.settings,
                    inProgressSessionId = sessions.firstOrNull { it.status == SessionStatus.IN_PROGRESS }?.id,
                )
            }.collect { set(it) }
        }
        launch { services.billing.refresh() }
    }
}
