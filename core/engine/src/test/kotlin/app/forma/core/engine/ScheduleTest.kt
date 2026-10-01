package app.forma.core.engine

import app.forma.core.model.ReturnPlan
import app.forma.core.model.Schedule
import app.forma.core.model.ScheduleMove
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Acceptance criteria 6 and 7 at the engine level. */
class ScheduleTest {
    private val monday = LocalDate.of(2026, 9, 28)
    private val schedule = Schedule(days = setOf(1, 3, 5))

    @Test
    fun `missed planned days are never counted as completed`() {
        val friday = monday.plusDays(4)
        val summary = WeekPlanner.summary(friday, schedule, emptyList(), listOf(SessionDay(monday, complete = true)))
        assertEquals(3, summary.plannedCount)
        assertEquals(1, summary.completedCount)
        assertEquals(DayState.DONE, summary.days[0].state)
        assertEquals(DayState.NOT_DONE, summary.days[2].state)
        assertEquals(DayState.PLANNED, summary.days[4].state)
        assertEquals("1 of 3 planned sessions done this week", summary.accessibilityText)
    }

    @Test
    fun `an unplanned session still counts and partial sessions are shown as partial`() {
        val summary = WeekPlanner.summary(
            monday.plusDays(6), schedule, emptyList(),
            listOf(SessionDay(monday.plusDays(1), complete = false), SessionDay(monday.plusDays(6), complete = true)),
        )
        assertEquals(DayState.PARTIAL, summary.days[1].state)
        assertEquals(DayState.DONE, summary.days[6].state)
        assertEquals(2, summary.completedCount)
    }

    @Test
    fun `moving a session moves the plan without adding sessions`() {
        val moves = listOf(ScheduleMove(monday.toString(), monday.plusDays(1).toString()))
        val planned = WeekPlanner.plannedDates(monday, schedule, moves)
        assertEquals(setOf(monday.plusDays(1), monday.plusDays(2), monday.plusDays(4)), planned)
        assertEquals(3, planned.size)
    }

    @Test
    fun `missing a day or two triggers no check-in`() {
        assertEquals(ReturnStatus.None, ReturnCheck.evaluate(monday, monday.plusDays(2), null))
        assertEquals(ReturnStatus.None, ReturnCheck.evaluate(monday, monday.plusDays(7), null))
        assertEquals(ReturnStatus.None, ReturnCheck.evaluate(null, monday, null))
    }

    @Test
    fun `short and long breaks are distinguished`() {
        assertIs<ReturnStatus.ShortBreak>(ReturnCheck.evaluate(monday, monday.plusDays(10), null))
        val long = assertIs<ReturnStatus.LongBreak>(ReturnCheck.evaluate(monday, monday.plusDays(40), null))
        assertEquals(40, long.daysAway)
        assertEquals(TrainingRules.RETURN_LONG_BREAK_EASE_SESSIONS, ReturnCheck.easeSessionsFor(long))
    }

    @Test
    fun `an answered check-in is not asked again for the same break`() {
        val answered = ReturnPlan(answeredOn = monday.plusDays(30).toString(), easeBack = false, sessionsRemaining = 0)
        assertEquals(ReturnStatus.None, ReturnCheck.evaluate(monday, monday.plusDays(31), answered))
        val stale = ReturnPlan(answeredOn = monday.minusDays(60).toString(), easeBack = true, sessionsRemaining = 0)
        assertTrue(ReturnCheck.evaluate(monday, monday.plusDays(31), stale) is ReturnStatus.LongBreak)
    }

    @Test
    fun `next planned date skips rest days`() {
        assertEquals(monday.plusDays(2), WeekPlanner.nextPlannedDate(monday, schedule, emptyList()))
        assertEquals(monday.plusDays(7), WeekPlanner.nextPlannedDate(monday.plusDays(4), schedule, emptyList()))
    }
}
