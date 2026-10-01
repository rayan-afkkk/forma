package app.forma.android

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.forma.BuildConfig
import app.forma.android.billing.BillingFactory
import app.forma.android.billing.EntitlementCache
import app.forma.android.data.ALL_MIGRATIONS
import app.forma.android.data.DataStoreUserStateRepository
import app.forma.android.data.FormaDatabase
import app.forma.android.data.RoomEquipmentRepository
import app.forma.android.data.RoomExclusionRepository
import app.forma.android.data.RoomFavoritesRepository
import app.forma.android.data.RoomProgramRepository
import app.forma.android.data.RoomSessionRepository
import app.forma.android.data.RoomTransactionRunner
import app.forma.android.reminders.WorkManagerReminders
import app.forma.core.domain.ProductEvents
import app.forma.core.domain.Repositories
import app.forma.core.domain.SystemClock
import app.forma.presentation.AppInfo
import app.forma.presentation.AppServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

// Preferences and the entitlement cache. A corrupt file is replaced with defaults rather than
// crashing on start; workout history lives in Room and is unaffected.
private val Context.userStore by preferencesDataStore(
    name = "user",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)
private val Context.billingStore by preferencesDataStore(
    name = "billing",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Manual dependency injection: one container per process, created by [FormaApplication].
 * The app is small enough that a DI framework would add build time and indirection without
 * removing any real complexity; everything is constructed here and passed explicitly.
 */
class AppContainer(context: Context, currentActivity: () -> android.app.Activity?) {
    private val appContext = context.applicationContext

    /** Process-wide scope for work that must outlive a screen (billing, reminder scheduling). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val database: FormaDatabase = Room.databaseBuilder(appContext, FormaDatabase::class.java, FormaDatabase.NAME)
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    private val now: () -> Long = { SystemClock.nowMillis() }

    val repositories = Repositories(
        userState = DataStoreUserStateRepository(appContext.userStore),
        equipment = RoomEquipmentRepository(database.equipment(), now),
        exclusions = RoomExclusionRepository(database.exclusions()),
        favorites = RoomFavoritesRepository(database.favorites(), now),
        programs = RoomProgramRepository(database.programs()),
        sessions = RoomSessionRepository(database.sessions()),
        transactions = RoomTransactionRunner(database),
    )

    val billing = BillingFactory.create(
        context = appContext,
        scope = scope,
        cache = EntitlementCache(appContext.billingStore),
        clock = SystemClock,
        currentActivity = currentActivity,
    )

    val reminders = WorkManagerReminders(appContext)

    /**
     * Minimal product events (see docs/PRIVACY_DRAFT.md). No analytics SDK is included and nothing
     * leaves the device; debug builds log events to Logcat so flows can be checked by hand.
     */
    private val events = ProductEvents { event -> if (BuildConfig.DEBUG) Log.d("FormaEvents", event.name) }

    val services = AppServices(
        repos = repositories,
        clock = SystemClock,
        billing = billing,
        reminders = reminders,
        info = AppInfo(versionName = BuildConfig.VERSION_NAME, isDebugBuild = BuildConfig.DEBUG, supportEmail = ""),
        events = events,
    )

    /** Re-plans the next reminder from the stored settings in the device's current time zone. */
    fun rescheduleReminders() {
        scope.launch {
            val user = repositories.userState.current()
            reminders.update(user.settings.reminders, user.schedule)
        }
    }
}
