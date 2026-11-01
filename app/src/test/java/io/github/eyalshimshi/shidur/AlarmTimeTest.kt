package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.data.nextAlarmTime
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmTimeTest {

    private val zone = ZoneId.of("Asia/Jerusalem")

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    // 1 November 2026 is a Sunday.

    @Test
    fun laterTodayRingsToday() {
        assertEquals(at(1, 7), nextAlarmTime(at(1, 6, 30), 7, 0, emptySet()))
    }

    @Test
    fun timeThatHasPassedRingsTomorrow() {
        assertEquals(at(2, 7), nextAlarmTime(at(1, 7), 7, 0, emptySet()))
        assertEquals(at(2, 7), nextAlarmTime(at(1, 22, 15), 7, 0, emptySet()))
    }

    @Test
    fun repeatingAlarmWaitsForAChosenDay() {
        val workdays = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)
        assertEquals(at(1, 7), nextAlarmTime(at(1, 6), 7, 0, workdays))
        assertEquals(at(2, 7), nextAlarmTime(at(1, 8), 7, 0, workdays))
        // Thursday evening: the next workday is Sunday.
        assertEquals(at(8, 7), nextAlarmTime(at(5, 20), 7, 0, workdays))
    }

    @Test
    fun weeklyAlarmCanBeSixDaysAway() {
        assertEquals(at(7, 9, 30), nextAlarmTime(at(1, 10), 9, 30, setOf(DayOfWeek.SATURDAY)))
    }
}
