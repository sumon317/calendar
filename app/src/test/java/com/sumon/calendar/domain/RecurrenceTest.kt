package com.sumon.calendar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RecurrenceTest {

    private val testZone = ZoneId.of("UTC")

    @Test
    fun testDailyRecurrenceToRRule() {
        val recurrence = SimpleRecurrence(
            freq = RecurrenceFreq.Daily,
            interval = 2,
            end = RecurrenceEnd.Never,
        )
        val rrule = recurrence.toRRule(testZone)
        assertEquals("FREQ=DAILY;INTERVAL=2", rrule)

        val parsed = parseSimpleRecurrence(rrule, testZone)
        assertNotNull(parsed)
        assertEquals(RecurrenceFreq.Daily, parsed!!.freq)
        assertEquals(2, parsed.interval)
        assertEquals(RecurrenceEnd.Never, parsed.end)
    }

    @Test
    fun testWeeklyRecurrenceWithDays() {
        val recurrence = SimpleRecurrence(
            freq = RecurrenceFreq.Weekly,
            interval = 1,
            end = RecurrenceEnd.Never,
            byDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        )
        val rrule = recurrence.toRRule(testZone)
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", rrule)

        val parsed = parseSimpleRecurrence(rrule, testZone)
        assertNotNull(parsed)
        assertEquals(RecurrenceFreq.Weekly, parsed!!.freq)
        assertEquals(1, parsed.interval)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), parsed.byDays)
    }

    @Test
    fun testRecurrenceWithCountEnd() {
        val recurrence = SimpleRecurrence(
            freq = RecurrenceFreq.Monthly,
            interval = 1,
            end = RecurrenceEnd.Count(10),
        )
        val rrule = recurrence.toRRule(testZone)
        assertEquals("FREQ=MONTHLY;COUNT=10", rrule)

        val parsed = parseSimpleRecurrence(rrule, testZone)
        assertNotNull(parsed)
        assertEquals(RecurrenceFreq.Monthly, parsed!!.freq)
        assertEquals(RecurrenceEnd.Count(10), parsed.end)
    }

    @Test
    fun testRecurrenceWithUntilEnd() {
        val untilDate = LocalDate.of(2026, 12, 31)
        val recurrence = SimpleRecurrence(
            freq = RecurrenceFreq.Yearly,
            interval = 1,
            end = RecurrenceEnd.Until(untilDate),
        )
        val rrule = recurrence.toRRule(testZone)
        assertTrue(rrule.startsWith("FREQ=YEARLY;UNTIL=20261231T235959Z"))

        val parsed = parseSimpleRecurrence(rrule, testZone)
        assertNotNull(parsed)
        assertEquals(RecurrenceFreq.Yearly, parsed!!.freq)
        assertTrue(parsed.end is RecurrenceEnd.Until)
        assertEquals(untilDate, (parsed.end as RecurrenceEnd.Until).date)
    }

    @Test
    fun testRruleTruncatedAt() {
        val originalRrule = "FREQ=WEEKLY;INTERVAL=1;BYDAY=MO,WE;COUNT=20"
        val untilMillis = 1780000000000L // arbitrary timestamp
        val truncated = rruleTruncatedAt(originalRrule, untilMillis)

        assertTrue(truncated.contains("FREQ=WEEKLY"))
        assertTrue(truncated.contains("BYDAY=MO,WE"))
        assertTrue(truncated.contains("UNTIL="))
        assertTrue(!truncated.contains("COUNT="))
    }

    @Test
    fun testRecurrenceToHumanReadable() {
        val dailyRule = "FREQ=DAILY;INTERVAL=1"
        assertEquals("Repeats Daily", recurrenceToHumanReadable(dailyRule))

        val weeklyRule = "FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH"
        assertEquals("Repeats Every 2 weeks on Tuesday, Thursday", recurrenceToHumanReadable(weeklyRule))

        val countRule = "FREQ=MONTHLY;COUNT=5"
        assertEquals("Repeats Monthly, 5 times", recurrenceToHumanReadable(countRule))
    }
}
