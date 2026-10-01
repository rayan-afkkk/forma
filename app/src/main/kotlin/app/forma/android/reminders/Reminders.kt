package app.forma.android.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.forma.R
import app.forma.android.FormaApplication
import app.forma.android.MainActivity
import app.forma.core.domain.PlanKey
import app.forma.core.domain.TodayDay
import app.forma.core.model.ReminderSettings
import app.forma.core.model.Schedule
import app.forma.presentation.ReminderScheduler
import app.forma.presentation.ReminderTiming
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Optional workout reminders with WorkManager. One notification on planned days at the chosen
 * time; nothing on rest days or once the day's session is done. Copy is neutral and never
 * guilt-based. Times follow the device's current time zone.
 */
class WorkManagerReminders(private val context: Context) : ReminderScheduler {

    override fun update(reminders: ReminderSettings, schedule: Schedule) {
        val workManager = WorkManager.getInstance(context)
        if (!reminders.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        val next = ReminderTiming.next(now, reminders.minuteOfDay, schedule.days) ?: run {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delayMs = java.time.Duration.between(now, next).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "forma_workout_reminder"
        const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 1001

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun canNotify(context: Context): Boolean {
            val permitted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun show(context: Context, title: String, text: String) {
            if (!canNotify(context)) return
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permission was revoked between the check and the call; nothing to show.
            }
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val services = (applicationContext as FormaApplication).container.services
        val user = services.repos.userState.current()
        if (user.onboardingComplete && user.settings.reminders.enabled) {
            val snapshot = services.planning.snapshot()
            val today = services.planning.today(snapshot, services.planning.adjustments(PlanKey.ProgramNext))
            val day = today.day
            if (day is TodayDay.Planned && today.inProgress == null) {
                val plan = day.plan.plan
                WorkManagerReminders.show(
                    applicationContext,
                    title = applicationContext.getString(R.string.reminder_title),
                    text = applicationContext.getString(
                        R.string.reminder_text,
                        plan.title,
                        ((plan.estimatedSeconds + 59) / 60).coerceAtLeast(1),
                    ),
                )
            }
        }
        // Schedule the next reminder from the current settings and time zone.
        services.reminders.update(user.settings.reminders, user.schedule)
        return Result.success()
    }
}

/** Re-plans reminders after a time-zone or clock change so they stay at local time. */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? FormaApplication ?: return
        app.container.rescheduleReminders()
    }
}
