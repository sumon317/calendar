package com.sumon.calendar.domain.ics

import com.sumon.calendar.domain.Availability
import com.sumon.calendar.domain.EventStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IcsParserWriterTest {

    @Test
    fun testIcsWriteAndParseRoundtrip() {
        val writer = IcsWriter()
        val parser = IcsParser()

        val event = IcsEvent(
            uid = "test-event-12345",
            summary = "Team Architecture Sync",
            startEpochMillis = 1780000000000L,
            endEpochMillis = 1780003600000L,
            isAllDay = false,
            zoneId = "UTC",
            recurrenceRule = "FREQ=WEEKLY;BYDAY=TU",
            location = "Conference Room A",
            description = "Detailed project roadmap discussion.",
            reminderMinutes = listOf(10, 60),
            status = EventStatus.Confirmed,
            availability = Availability.Busy,
        )

        val icsText = writer.writeCalendar(listOf(event), Instant.ofEpochMilli(1780000000000L))
        assertTrue(icsText.contains("BEGIN:VCALENDAR"))
        assertTrue(icsText.contains("BEGIN:VEVENT"))
        assertTrue(icsText.contains("SUMMARY:Team Architecture Sync"))
        assertTrue(icsText.contains("LOCATION:Conference Room A"))
        assertTrue(icsText.contains("END:VCALENDAR"))

        val parsedEvents = parser.parse(icsText)
        assertEquals(1, parsedEvents.size)

        val parsed = parsedEvents.first()
        assertEquals("test-event-12345", parsed.uid)
        assertEquals("Team Architecture Sync", parsed.summary)
        assertEquals("Conference Room A", parsed.location)
        assertEquals("Detailed project roadmap discussion.", parsed.description)
        assertEquals(1780000000000L, parsed.startEpochMillis)
        assertEquals(1780003600000L, parsed.endEpochMillis)
        assertEquals(EventStatus.Confirmed, parsed.status)
        assertEquals(listOf(10, 60), parsed.reminderMinutes)
    }

    @Test
    fun testAllDayEventIcs() {
        val writer = IcsWriter()
        val parser = IcsParser()

        val event = IcsEvent(
            uid = "allday-123",
            summary = "National Holiday",
            startEpochMillis = 1780000000000L,
            endEpochMillis = 1780086400000L,
            isAllDay = true,
            zoneId = "UTC",
        )

        val icsText = writer.writeCalendar(listOf(event))
        assertTrue(icsText.contains("DTSTART;VALUE=DATE:"))

        val parsed = parser.parse(icsText)
        assertEquals(1, parsed.size)
        assertTrue(parsed.first().isAllDay)
        assertEquals("National Holiday", parsed.first().summary)
    }
}
