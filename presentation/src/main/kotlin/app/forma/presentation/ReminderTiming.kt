package app.forma.presentation

import java.time.ZonedDateTime

/**
 * When the next workout reminder should fire: the next scheduled training day at the chosen
 * time, in the device's current time zone. Recomputed after every reminder, on app start and on
 * time-zone changes, so travel and daylight-saving changes keep reminders at local time.
 */
object ReminderTiming {
    fun next(now: ZonedDateTime, minuteOfDay: Int, days: Set<Int>): ZonedDateTime? {
        if (days.isEmpty()) return null
        val hour = minuteOfDay / 60
        val minute = minuteOfDay % 60
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (date.dayOfWeek.value !in days) continue
            val candidate = date.atTime(hour, minute).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
