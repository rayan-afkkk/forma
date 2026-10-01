package app.forma.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.forma.android.reminders.WorkManagerReminders
import java.lang.ref.WeakReference

class FormaApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** The resumed activity, needed only to launch the Google Play purchase sheet. */
    @Volatile private var resumed: WeakReference<Activity>? = null

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ResumedActivityTracker())
        container = AppContainer(this) { resumed?.get() }
        WorkManagerReminders.createChannel(this)
        container.rescheduleReminders()
    }

    private inner class ResumedActivityTracker : ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumed = WeakReference(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (resumed?.get() === activity) resumed = null
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
