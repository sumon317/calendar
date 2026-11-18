package com.sumon.calendar.domain.ics

import com.sumon.calendar.domain.Availability
import com.sumon.calendar.domain.EventStatus
import com.sumon.calendar.domain.toUtcBasic
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

const val ICS_PROD_ID: String = "-//Sumon//Calendar//EN"
private const val ICS_CRLF = "\r\n"
private val BASIC_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
private val BASIC_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US)

class IcsWriter(private val prodId: String = ICS_PROD_ID) {

    fun writeCalendar(events: List<IcsEvent>, dtStamp: Instant = Instant.now()): String {
        val lines = buildList {
            add("BEGIN:VCALENDAR")
            add("VERSION:2.0")
            add("PRODID:$prodId")
            add("CALSCALE:GREGORIAN")
            events.forEach { appendEvent(it, dtStamp) }
            add("END:VCALENDAR")
        }
        return lines.joinToString(ICS_CRLF, postfix = ICS_CRLF) { foldLine(it) }
    }

    private fun MutableList<String>.appendEvent(event: IcsEvent, dtStamp: Instant) {
        add("BEGIN:VEVENT")
        add("UID:${event.uid}")
        add("DTSTAMP:${dtStamp.toUtcBasic()}")
        add("SUMMARY:${escapeText(event.summary)}")
        appendTimes(event)
        event.recurrenceRule?.takeIf { it.isNotBlank() }?.let {
            add("RRULE:${it.removePrefix("RRULE:")}")
        }
        event.location?.takeIf { it.isNotBlank() }?.let {
            add("LOCATION:${escapeText(it)}")
        }
        event.description?.takeIf { it.isNotBlank() }?.let {
            add("DESCRIPTION:${escapeText(it)}")
        }
        add("STATUS:${statusValue(event.status)}")
        add("TRANSP:${transpValue(event.availability)}")
        event.calendarName?.takeIf { it.isNotBlank() }?.let {
            add("X-CALENDAR-NAME:${escapeText(it)}")
        }
        event.reminderMinutes.filter { it >= 0 }.distinct().forEach { minutes ->
            add("BEGIN:VALARM")
            add("ACTION:DISPLAY")
            add("DESCRIPTION:Reminder")
            add("TRIGGER:-PT${minutes}M")
            add("END:VALARM")
        }
        add("END:VEVENT")
    }

    private fun MutableList<String>.appendTimes(event: IcsEvent) {
        if (event.isAllDay) {
            val startDate = Instant.ofEpochMilli(event.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val endDate = Instant.ofEpochMilli(event.endEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
            add("DTSTART;VALUE=DATE:${BASIC_DATE_FORMAT.format(startDate)}")
            add("DTEND;VALUE=DATE:${BASIC_DATE_FORMAT.format(endDate)}")
        } else if (!event.recurrenceRule.isNullOrBlank()) {
            val zone = try { ZoneId.of(event.zoneId) } catch (_: Exception) { ZoneId.systemDefault() }
            val startLocal = Instant.ofEpochMilli(event.startEpochMillis).atZone(zone).toLocalDateTime()
            val endLocal = Instant.ofEpochMilli(event.endEpochMillis).atZone(zone).toLocalDateTime()
            add("DTSTART;TZID=${zone.id}:${BASIC_DATETIME_FORMAT.format(startLocal)}")
            add("DTEND;TZID=${zone.id}:${BASIC_DATETIME_FORMAT.format(endLocal)}")
        } else {
            val startInstant = Instant.ofEpochMilli(event.startEpochMillis)
            val endInstant = Instant.ofEpochMilli(event.endEpochMillis)
            add("DTSTART:${startInstant.toUtcBasic()}")
            add("DTEND:${endInstant.toUtcBasic()}")
        }
    }

    private fun statusValue(status: EventStatus): String = when (status) {
        EventStatus.Confirmed -> "CONFIRMED"
        EventStatus.Tentative -> "TENTATIVE"
        EventStatus.Cancelled -> "CANCELLED"
    }

    private fun transpValue(availability: Availability): String = when (availability) {
        Availability.Busy -> "OPAQUE"
        Availability.Free, Availability.Tentative -> "TRANSPARENT"
    }

    private fun escapeText(text: String): String =
        text.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")

    private fun foldLine(line: String): String {
        if (line.length <= 75) return line
        val sb = StringBuilder()
        var rem = line
        var first = true
        while (rem.isNotEmpty()) {
            val limit = if (first) 75 else 74
            if (rem.length <= limit) {
                if (!first) sb.append(" ")
                sb.append(rem)
                break
            }
            val slice = rem.substring(0, limit)
            if (!first) sb.append(" ")
            sb.append(slice).append(ICS_CRLF)
            rem = rem.substring(limit)
            first = false
        }
        return sb.toString()
    }
}
