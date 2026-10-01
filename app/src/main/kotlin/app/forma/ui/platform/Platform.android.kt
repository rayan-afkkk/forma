package app.forma.ui.platform

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* Android implementations of the platform seams described in PlatformContracts.kt. */

@Composable
fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit) = BackHandler(enabled, onBack)

@Composable
fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Keeps a pure-Kotlin state holder alive across configuration changes, scoped to the screen's back stack entry. */
class HolderViewModel(factory: (CoroutineScope) -> Any) : ViewModel() {
    val holder: Any = factory(viewModelScope)
}

/** The key must identify the holder type and its arguments, e.g. "preview:<planKey>". */
@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> rememberStateHolder(key: String, factory: (CoroutineScope) -> T): T =
    viewModel(key = key) { HolderViewModel(factory) }.holder as T

@Composable
fun <T> StateFlow<T>.collectUiState(): State<T> = collectAsStateWithLifecycle()

@Composable
fun rememberCuePlayer(): CuePlayer {
    val context = LocalContext.current
    val player = remember(context) { AndroidCuePlayer(context.applicationContext) }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private class AndroidCuePlayer(private val context: Context) : CuePlayer {
    private var tone: ToneGenerator? = null

    override fun play(sound: Boolean, haptic: Boolean) {
        if (sound) {
            // Uses the notification stream so it respects silent mode and the person's volume.
            val generator = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }.getOrNull()?.also { tone = it }
            generator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        }
        if (haptic) vibrator()?.takeIf { it.hasVibrator() }?.let { v ->
            runCatching { v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 120), -1)) }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    fun release() {
        tone?.release()
        tone = null
    }
}

/** Holds the export while the system picker is open, so a configuration change does not lose it. */
class PendingExportViewModel : ViewModel() {
    var content: String? = null
}

private class CreateTypedDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data?.takeIf { resultCode == Activity.RESULT_OK }
}

private const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

@Composable
fun rememberFileActions(onSaved: (Boolean) -> Unit, onRead: (String?) -> Unit): FileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = rememberUpdatedState(onSaved)
    val read = rememberUpdatedState(onRead)
    val pending = viewModel<PendingExportViewModel>()

    val createLauncher = rememberLauncherForActivityResult(CreateTypedDocument()) { uri ->
        val content = pending.content
        pending.content = null
        // Cancelling the picker is not an error; say nothing.
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) } != null
                }.getOrDefault(false)
            }
            saved.value(ok)
        }
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readNBytesCompat(MAX_IMPORT_BYTES + 1)
                        if (bytes.size > MAX_IMPORT_BYTES) null else bytes.toString(Charsets.UTF_8)
                    }
                }.getOrNull()
            }
            read.value(text)
        }
    }
    return remember(createLauncher, openLauncher) {
        object : FileActions {
            override fun save(suggestedName: String, mimeType: String, content: String) {
                pending.content = content
                runCatching { createLauncher.launch(suggestedName to mimeType) }.onFailure {
                    pending.content = null
                    saved.value(false)
                }
            }

            override fun pickBackup() {
                // Some file providers report JSON as plain text or a generic binary type.
                runCatching { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    .onFailure { read.value(null) }
            }
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Long): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    var total = 0L
    while (total < limit) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), limit - total).toInt())
        if (n < 0) break
        out.write(buffer, 0, n)
        total += n
    }
    return out.toByteArray()
}

@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> result.value(granted) }
    return remember(launcher) {
        {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted || Build.VERSION.SDK_INT < 33) result.value(granted)
            else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** True when the person turned animations off in system settings (Accessibility › Remove animations). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
}
