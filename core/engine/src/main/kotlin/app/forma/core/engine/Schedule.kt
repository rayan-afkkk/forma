package app.forma.core.engine

import app.forma.core.model.ReturnPlan
import app.forma.core.model.Schedule
import app.forma.core.model.ScheduleMove
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Result of checking how long it has been since the last session. */
sealed interface ReturnStatus {
    data object None : ReturnStatus

    /** A short break (8–20 days by the draft rules). Offer an optional easier first session. */
    data class ShortBreak(val daysAway: Int) : ReturnStatus

    /** A longer break. Recommend easing back in over a couple of sessions. */
    data class LongBreak(val daysAway: Int) : ReturnStatus
}

object ReturnCheck {
    /**
     * Missing a day or two never triggers anything. Only a real gap since the last finished session
     * prompts a check-in, and only once per gap: answering it records [ReturnPlan.answeredOn].
     */
    fun evaluate(lastSessionDate: LocalDate?, today: LocalDate, answered: ReturnPlan?): ReturnStatus {
        if (lastSessionDate == null) return ReturnStatus.None
        val days = ChronoUnit.DAYS.between(lastSessionDate, today).toInt()
        if (days <= TrainingRules.RETURN_NO_CHECK_IN_MAX_DAYS) return ReturnStatus.None
        if (answered != null && !LocalDate.parse(answered.answeredOn).isBefore(lastSessionDate.plusDays(1))) {
            return ReturnStatus.None
        }
        return if (days <= TrainingRules.RETURN_SHORT_BREAK_MAX_DAYS) ReturnStatus.ShortBreak(days) else ReturnStatus.LongBreak(days)
    }

    fun easeSessionsFor(status: ReturnStatus): Int = when (status) {
        is ReturnStatus.ShortBreak -> TrainingRules.RETURN_SHORT_BREAK_EASE_SESSIONS
        is ReturnStatus.LongBreak -> TrainingRules.RETURN_LONG_BREAK_EASE_SESSIONS
        ReturnStatus.None -> 0
    }
}

enum class DayState {
    /** Not a planned day and nothing done. */
    REST,

    /** Planned, today or later, not done yet. */
    PLANNED,

    /** At least one finished session that day (planned or not). */
    DONE,

    /** At least one partial session and no complete one. */
    PARTIAL,

    /** A planned day in the past with no session. Shown neutrally; never counted as done. */
    NOT_DONE,
}

data class DayStatus(
    val date: LocalDate,
    val planned: Boolean,
    val state: DayState,
    val isToday: Boolean,
)

data class WeekSummary(
    val days: List<DayStatus>,
    val plannedCount: Int,
    val completedCount: Int,
) {
    val accessibilityText: String
        get() = "$completedCount of $plannedCount planned sessions done this week"
}

/** Finished session as the schedule sees it. */
data class SessionDay(val localDate: LocalDate, val complete: Boolean)

object WeekPlanner {

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Planned dates in the week containing [anyDay], after applying moves. */
    fun plannedDates(anyDay: LocalDate, schedule: Schedule, moves: List<ScheduleMove>): Set<LocalDate> {
        val start = weekStart(anyDay)
        val week = (0L until 7L).map { start.plusDays(it) }
        val planned = week.filter { it.dayOfWeek.value in schedule.days }.toMutableSet()
        for (move in moves) {
            val from = LocalDate.parse(move.fromDate)
            val to = LocalDate.parse(move.toDate)
            if (from in week) planned -= from
            if (to in week) planned += to
        }
        return planned
    }

    fun isPlanned(date: LocalDate, schedule: Schedule, moves: List<ScheduleMove>): Boolean =
        date in plannedDates(date, schedule, moves)

    fun summary(
        today: LocalDate,
        schedule: Schedule,
        moves: List<ScheduleMove>,
        sessions: List<SessionDay>,
    ): WeekSummary {
        val start = weekStart(today)
        val planned = plannedDates(today, schedule, moves)
        val byDate = sessions.groupBy { it.localDate }
        val days = (0L until 7L).map { offset ->
            val date = start.plusDays(offset)
            val done = byDate[date].orEmpty()
            val state = when {
                done.any { it.complete } -> DayState.DONE
                done.isNotEmpty() -> DayState.PARTIAL
                date in planned && date.isBefore(today) -> DayState.NOT_DONE
                date in planned -> DayState.PLANNED
                else -> DayState.REST
            }
            DayStatus(date, date in planned, state, date == today)
        }
        val completed = days.count { it.state == DayState.DONE || it.state == DayState.PARTIAL }
        return WeekSummary(days, planned.size, completed)
    }

    /** Next planned date strictly after [after], looking up to five weeks ahead. */
    fun nextPlannedDate(after: LocalDate, schedule: Schedule, moves: List<ScheduleMove>): LocalDate? {
        var date = after.plusDays(1)
        repeat(35) {
            if (isPlanned(date, schedule, moves)) return date
            date = date.plusDays(1)
        }
        return null
    }

    /** Days a planned session on [from] can move to: the rest of this week plus the next few days, not already planned. */
    fun moveTargets(from: LocalDate, today: LocalDate, schedule: Schedule, moves: List<ScheduleMove>): List<LocalDate> =
        (0L..6L).map { today.plusDays(it) }
            .filter { it != from && !isPlanned(it, schedule, moves) }
}
