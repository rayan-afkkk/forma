package app.forma.presentation

import app.forma.core.model.Load
import app.forma.core.model.Target
import app.forma.core.model.WeightUnit
import app.forma.core.model.WorkoutItem
import app.forma.core.model.format
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * English copy formatting. Strings live in code in this version so the same screens can be
 * verified on the JVM; moving them to localisable resources is listed in docs/READINESS.md.
 */
object Format {
    private val locale = Locale.ENGLISH

    fun target(target: Target, perSide: Boolean): String {
        val base = when (target) {
            is Target.Reps -> if (target.min == target.max) "${target.min} reps" else "${target.min}–${target.max} reps"
            is Target.Time -> seconds(target.seconds)
        }
        return if (perSide) "$base each side" else base
    }

    fun setsAndTarget(item: WorkoutItem): String {
        val sets = if (item.sets == 1) "" else "${item.sets} × "
        return sets + target(item.target, item.perSide)
    }

    fun load(load: Load?, displayUnit: WeightUnit? = null): String? = load?.format(displayUnit ?: load.unit)

    fun itemDetail(item: WorkoutItem): String =
        listOfNotNull(setsAndTarget(item), load(item.load)).joinToString(" · ")

    fun seconds(seconds: Int): String = when {
        seconds < 60 -> "$seconds sec"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} sec"
    }

    fun rest(seconds: Int): String = if (seconds <= 0) "No rest" else "${seconds(seconds)} rest"

    fun aboutMinutes(minutes: Int): String = "About $minutes min"

    fun minutes(totalSeconds: Int): String {
        val minutes = (totalSeconds + 30) / 60
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    /** "0:45", "12:05", "1:02:03". */
    fun clock(millis: Long): String {
        val total = ((millis + 999) / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun elapsed(millis: Long): String {
        val total = (millis / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
    }

    fun dayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, locale)
    fun shortDayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale)
    fun dayLetter(day: DayOfWeek): String = day.getDisplayName(TextStyle.NARROW, locale)

    fun longDate(date: LocalDate): String =
        "${dayName(date.dayOfWeek)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, locale)}"

    fun shortDate(date: LocalDate): String =
        "${shortDayName(date.dayOfWeek)} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, locale)}"

    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> if (date.isBefore(today.plusDays(7)) && date.isAfter(today)) dayName(date.dayOfWeek) else shortDate(date)
    }

    fun days(days: Set<Int>): String = when {
        days.isEmpty() -> "No days chosen"
        days.size == 7 -> "Every day"
        else -> days.sorted().joinToString(", ") { shortDayName(DayOfWeek.of(it)) }
    }

    fun timeOfDay(minuteOfDay: Int): String {
        val h = minuteOfDay / 60
        val m = minuteOfDay % 60
        val suffix = if (h < 12) "am" else "pm"
        val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
        return if (m == 0) "$h12 $suffix" else String.format(Locale.ROOT, "%d:%02d %s", h12, m, suffix)
    }

    fun count(n: Int, singular: String, plural: String = singular + "s") = "$n ${if (n == 1) singular else plural}"

    fun initials(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> ""
            parts.size == 1 -> parts[0].take(1).uppercase()
            else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
        }
    }

    fun greeting(hour: Int, name: String): String {
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        return if (name.isBlank()) part else "$part, ${name.trim()}"
    }
}
