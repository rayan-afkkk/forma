package app.forma.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/*
 * JVM verification harness implementations of the platform seams. They record what the shared UI
 * asked for so tests can assert on it (back handling, keep-screen-on, cues, file actions).
 */

object HarnessSignals {
    val backHandlers = ArrayDeque<() -> Unit>()
    var keepScreenOn = false
    var cues = 0
    val savedFiles = mutableListOf<Triple<String, String, String>>()
    var importContent: String? = null
    var notificationPermission = true

    /** Simulates the system back button: the most recently registered enabled handler wins. */
    fun pressBack(): Boolean {
        val handler = backHandlers.lastOrNull() ?: return false
        handler()
        return true
    }

    fun reset() {
        backHandlers.clear()
        keepScreenOn = false
        cues = 0
        savedFiles.clear()
        importContent = null
        notificationPermission = true
    }
}

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
    val current = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        val handler: () -> Unit = { current.value() }
        if (enabled) HarnessSignals.backHandlers.addLast(handler)
        onDispose { HarnessSignals.backHandlers.remove(handler) }
    }
}

@Composable
fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        HarnessSignals.keepScreenOn = enabled
        onDispose { HarnessSignals.keepScreenOn = false }
    }
}

@Composable
fun <T : Any> rememberStateHolder(key: String, factory: (CoroutineScope) -> T): T {
    val scope = rememberCoroutineScope()
    return remember(key) { factory(scope) }
}

@Composable
fun <T> StateFlow<T>.collectUiState(): State<T> = collectAsState()

@Composable
fun rememberCuePlayer(): CuePlayer = remember { CuePlayer { _, _ -> HarnessSignals.cues++ } }

@Composable
fun rememberFileActions(onSaved: (Boolean) -> Unit, onRead: (String?) -> Unit): FileActions {
    val saved = rememberUpdatedState(onSaved)
    val read = rememberUpdatedState(onRead)
    return remember {
        object : FileActions {
            override fun save(suggestedName: String, mimeType: String, content: String) {
                HarnessSignals.savedFiles += Triple(suggestedName, mimeType, content)
                saved.value(true)
            }

            override fun pickBackup() = read.value(HarnessSignals.importContent)
        }
    }
}

@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val result = rememberUpdatedState(onResult)
    return remember { { result.value(HarnessSignals.notificationPermission) } }
}

@Composable
fun rememberReduceMotion(): Boolean = false
