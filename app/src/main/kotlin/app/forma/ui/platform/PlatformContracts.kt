package app.forma.ui.platform

/*
 * Shared UI code calls a small set of platform functions in this package:
 *
 *   PlatformBackHandler(enabled, onBack)       – system back handling
 *   KeepScreenOn(enabled)                      – keeps the display on while in composition
 *   rememberStateHolder(key, factory)          – screen-scoped holder (a ViewModel on Android)
 *   StateFlow<T>.collectUiState()              – lifecycle-aware collection on Android
 *   rememberCuePlayer()                        – rest-finished sound and vibration
 *   rememberFileActions(onSaved, onRead)       – system file picker for export and import
 *   rememberNotificationPermission(onResult)   – asks for notification permission when needed
 *   rememberReduceMotion()                     – system "remove animations" setting
 *
 * Android implementations live in *.android.kt files next to this one; the JVM verification
 * harness provides its own. The interfaces below are shared.
 */

fun interface CuePlayer {
    fun play(sound: Boolean, haptic: Boolean)
}

interface FileActions {
    /** Opens the system "save as" picker and writes [content]. */
    fun save(suggestedName: String, mimeType: String, content: String)

    /** Opens the system file picker for a backup file and returns its text through onRead. */
    fun pickBackup()
}
