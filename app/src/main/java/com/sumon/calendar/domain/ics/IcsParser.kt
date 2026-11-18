package com.sumon.calendar.domain.ics

import com.sumon.calendar.domain.Availability
import com.sumon.calendar.domain.EventStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class IcsParser {

    fun parse(icsContent: String): List<ParsedIcsEvent> {
        val unfolded = unfoldLines(icsContent)
        val events = mutableListOf<ParsedIcsEvent>()

        var inEvent = false
        val currentEventLines = mutableListOf<String>()

        for (line in unfolded) {
            val trimmed = line.trim()
            if (trimmed.equals("BEGIN:VEVENT", ignoreCase = true)) {
                inEvent = true
                currentEventLines.clear()
            } else if (trimmed.equals("END:VEVENT", ignoreCase = true)) {
                if (inEvent) {
                    parseEvent(currentEventLines)?.let { events.add(it) }
                    inEvent = false
                }
            } else if (inEvent) {
                currentEventLines.add(line)
            }
        }
        return events
    }

    private fun unfoldLines(content: String): List<String> {
        val result = mutableListOf<String>()
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var current: StringBuilder? = null

        for (line in lines) {
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current?.append(line.substring(1))
            } else {
                if (current != null) {
                    result.add(current.toString())
                }
                current = StringBuilder(line)
            }
        }
        if (current != null) {
            result.add(current.toString())
        }
        return result
    }

    private fun parseEvent(lines: List<String>): ParsedIcsEvent? {
        var uid: String? = null
        var summary = "Untitled Event"
        var location: String? = null
        var description: String? = null
        var rrule: String? = null
        var status = EventStatus.Confirmed
        var availability = Availability.Busy
        var calendarName: String? = null

        var dtStartRaw: String? = null
        var dtStartParams = ""
        var dtEndRaw: String? = null
        var dtEndParams = ""
        var durationRaw: String? = null

        val reminders = mutableListOf<Int>()
        var inAlarm = false
        var currentTrigger: String? = null

        for (line in lines) {
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) continue
            val left = line.substring(0, colonIdx)
            val value = line.substring(colonIdx + 1)

            val semicolonIdx = left.indexOf(';')
            val name = (if (semicolonIdx > 0) left.substring(0, semicolonIdx) else left).trim().uppercase()
            val params = (if (semicolonIdx > 0) left.substring(semicolonIdx + 1) else "").trim()

            if (name == "BEGIN" && value.trim().equals("VALARM", ignoreCase = true)) {
                inAlarm = true
                currentTrigger = null
            } else if (name == "END" && value.trim().equals("VALARM", ignoreCase = true)) {
                if (inAlarm) {
                    currentTrigger?.let { parseTriggerMinutes(it)?.let { m -> reminders.add(m) } }
                    inAlarm = false
                }
            } else if (inAlarm) {
                if (name == "TRIGGER") {
                    currentTrigger = value.trim()
                }
            } else {
                when (name) {
                    "UID" -> uid = value.trim()
                    "SUMMARY" -> summary = unescapeText(value.trim())
                    "LOCATION" -> location = unescapeText(value.trim())
                    "DESCRIPTION" -> description = unescapeText(value.trim())
                    "RRULE" -> rrule = value.trim()
                    "STATUS" -> {
                        status = when (value.trim().uppercase()) {
                            "TENTATIVE" -> EventStatus.Tentative
                            "CANCELLED" -> EventStatus.Cancelled
                            else -> EventStatus.Confirmed
                        }
                    }
                    "TRANSP" -> {
                        availability = when (value.trim().uppercase()) {
                            "TRANSPARENT" -> Availability.Free
                            else -> Availability.Busy
                        }
                    }
                    "X-CALENDAR-NAME" -> calendarName = unescapeText(value.trim())
                    "DTSTART" -> {
                        dtStartRaw = value.trim()
                        dtStartParams = params
                    }
                    "DTEND" -> {
                        dtEndRaw = value.trim()
                        dtEndParams = params
                    }
                    "DURATION" -> durationRaw = value.trim()
                }
            }
        }

        if (dtStartRaw == null) return null

        val isAllDay = dtStartParams.uppercase().contains("VALUE=DATE") || dtStartRaw.length == 8

        val zoneId = extractZoneId(dtStartParams) ?: ZoneId.systemDefault().id
        val zone = try { ZoneId.of(zoneId) } catch (_: Exception) { ZoneId.systemDefault() }

        val startEpochMillis = parseDateOrDateTime(dtStartRaw, isAllDay, zone) ?: return null

        val endEpochMillis = if (dtEndRaw != null) {
            parseDateOrDateTime(dtEndRaw, isAllDay, zone) ?: (startEpochMillis + if (isAllDay) 86_400_000L else 3_600_000L)
        } else if (durationRaw != null) {
            val durMillis = parseDurationMillis(durationRaw)
            startEpochMillis + durMillis
        } else {
            startEpochMillis + if (isAllDay) 86_400_000L else 3_600_000L
        }

        return ParsedIcsEvent(
            uid = uid,
            summary = summary,
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            isAllDay = isAllDay,
            zoneId = zone.id,
            recurrenceRule = rrule,
            location = location,
            description = description,
            reminderMinutes = reminders.sorted().distinct(),
            status = status,
            availability = availability,
            calendarName = calendarName,
        )
    }

    private fun extractZoneId(params: String): String? {
        val tzidKey = "TZID="
        val idx = params.indexOf(tzidKey, ignoreCase = true)
        if (idx < 0) return null
        val sub = params.substring(idx + tzidKey.length)
        val endIdx = sub.indexOf(';').takeIf { it >= 0 } ?: sub.length
        return sub.substring(0, endIdx).trim().removeSurrounding("\"")
    }

    private fun parseDateOrDateTime(raw: String, isAllDay: Boolean, zone: ZoneId): Long? {
        val clean = raw.trim().uppercase()
        return try {
            if (isAllDay || clean.length == 8) {
                val date = LocalDate.parse(clean.substring(0, 8), DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US))
                date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else if (clean.endsWith("Z")) {
                val dt = LocalDateTime.parse(clean.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US))
                dt.atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
            } else if (clean.contains("T")) {
                val dt = LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.US))
                dt.atZone(zone).toInstant().toEpochMilli()
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDurationMillis(duration: String): Long {
        return try {
            val d = Duration.parse(duration)
            d.toMillis()
        } catch (_: Exception) {
            3_600_000L
        }
    }

    private fun parseTriggerMinutes(trigger: String): Int? {
        val clean = trigger.trim().uppercase()
        if (clean.startsWith("-PT") || clean.startsWith("PT")) {
            val str = if (clean.startsWith("-")) clean.substring(1) else clean
            return try {
                Duration.parse(str).toMinutes().toInt()
            } catch (_: Exception) {
                null
            }
        }
        if (clean.startsWith("-P") || clean.startsWith("P")) {
            val str = if (clean.startsWith("-")) clean.substring(1) else clean
            return try {
                Duration.parse(str).toMinutes().toInt()
            } catch (_: Exception) {
                null
            }
        }
        return null
    }

    private fun unescapeText(text: String): String =
        text.replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
}
