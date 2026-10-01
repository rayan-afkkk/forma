package app.forma.presentation

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReminderTimingTest {
    private val zone = ZoneId.of("Europe/London")
    private val mondayMorning = ZonedDateTime.of(2026, 9, 28, 7, 0, 0, 0, zone)

    @Test
    fun `fires later today when today is a training day`() {
        val next = ReminderTiming.next(mondayMorning, 18 * 60, setOf(1, 3, 5))!!
        assertEquals(mondayMorning.withHour(18), next)
    }

    @Test
    fun `skips to the next training day once today's time has passed`() {
        val evening = mondayMorning.withHour(19)
        val next = ReminderTiming.next(evening, 18 * 60, setOf(1, 3, 5))!!
        assertEquals(3, next.dayOfWeek.value)
        assertEquals(18, next.hour)
    }

    @Test
    fun `never fires on rest days and handles a single weekly day`() {
        val next = ReminderTiming.next(mondayMorning.withHour(20), 18 * 60, setOf(1))!!
        assertEquals(mondayMorning.plusDays(7).withHour(18), next)
        assertNull(ReminderTiming.next(mondayMorning, 18 * 60, emptySet()))
    }

    @Test
    fun `keeps local time across a daylight saving change`() {
        val beforeChange = ZonedDateTime.of(2026, 10, 24, 20, 0, 0, 0, zone) // Saturday; clocks go back on Sunday 25 Oct
        val next = ReminderTiming.next(beforeChange, 18 * 60, setOf(1))!!
        assertEquals(18, next.hour)
        assertEquals(26, next.dayOfMonth)
    }
}
