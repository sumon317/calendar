package com.sumon.calendar.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Standard RFC 5545 recurrence frequencies supported by Calendula / CalendarContract.
 */
enum class RecurrenceFreq {
    Daily,
    Weekly,
    Monthly,
    Yearly,
}

/**
 * Supported recurrence end specifications.
 */
sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd
    data class Until(val date: LocalDate) : RecurrenceEnd
    data class Count(val times: Int) : RecurrenceEnd
}

/**
 * Simple recurrence rule model.
 */
data class SimpleRecurrence(
    val freq: RecurrenceFreq,
    val interval: Int = 1,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
    val byDays: Set<DayOfWeek> = emptySet(),
)

internal val RRULE_DAY_CODES: Map<DayOfWeek, String> = mapOf(
    DayOfWeek.MONDAY to "MO",
    DayOfWeek.TUESDAY to "TU",
    DayOfWeek.WEDNESDAY to "WE",
    DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR",
    DayOfWeek.SATURDAY to "SA",
    DayOfWeek.SUNDAY to "SU",
)

private val DAY_CODE_TO_WEEKDAY: Map<String, DayOfWeek> =
    RRULE_DAY_CODES.entries.associate { (k, v) -> v to k }

private val BASIC_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.US)
private val BASIC_DATETIME_UTC_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.US).withZone(ZoneOffset.UTC)

/**
 * Formats an Instant into RFC 5545 basic UTC representation (e.g. 20260618T103000Z).
 */
fun Instant.toUtcBasic(): String = BASIC_DATETIME_UTC_FORMATTER.format(this)

/**
 * Parses basic date (yyyyMMdd) or basic datetime (yyyyMMddTHHmmssZ).
 */
fun parseBasicDateOrDateTime(raw: String, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
    val clean = raw.trim().uppercase()
    return try {
        if (clean.length == 8) {
            LocalDate.parse(clean, BASIC_DATE_FORMATTER)
        } else if (clean.contains("T")) {
            val instant = Instant.from(BASIC_DATETIME_UTC_FORMATTER.parse(clean))
            instant.atZone(zone).toLocalDate()
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Serializes [SimpleRecurrence] into an RFC 5545 RRULE string (without "RRULE:" prefix).
 */
fun SimpleRecurrence.toRRule(zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    append("FREQ=")
    append(
        when (freq) {
            RecurrenceFreq.Daily -> "DAILY"
            RecurrenceFreq.Weekly -> "WEEKLY"
            RecurrenceFreq.Monthly -> "MONTHLY"
            RecurrenceFreq.Yearly -> "YEARLY"
        }
    )
    if (interval > 1) {
        append(";INTERVAL=").append(interval)
    }
    if (freq == RecurrenceFreq.Weekly && byDays.isNotEmpty()) {
        append(";BYDAY=")
        append(byDays.sortedBy { it.value }.joinToString(",") { RRULE_DAY_CODES.getValue(it) })
    }
    when (val e = end) {
        RecurrenceEnd.Never -> Unit
        is RecurrenceEnd.Until -> {
            append(";UNTIL=")
            val endUtc = e.date.atTime(LocalTime.of(23, 59, 59)).atZone(zone).toInstant()
            append(endUtc.toUtcBasic())
        }
        is RecurrenceEnd.Count -> {
            append(";COUNT=").append(e.times)
        }
    }
}

/**
 * Parses an RFC 5545 RRULE string into [SimpleRecurrence], or null if unsupported/custom.
 */
fun parseSimpleRecurrence(
    rrule: String,
    zone: ZoneId = ZoneId.systemDefault(),
): SimpleRecurrence? {
    if (rrule.isBlank()) return null
    val parts = rrule.removePrefix("RRULE:")
        .split(';')
        .filter { it.isNotBlank() }
        .associate { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return null
            part.substring(0, eq).trim().uppercase() to part.substring(eq + 1).trim()
        }

    // If there are complex parts like BYSETPOS, BYMONTH, BYHOUR, return null so caller preserves raw
    if (parts.keys.any { it !in setOf("FREQ", "INTERVAL", "UNTIL", "COUNT", "WKST", "BYDAY") }) {
        return null
    }

    val freq = when (parts["FREQ"]?.uppercase()) {
        "DAILY" -> RecurrenceFreq.Daily
        "WEEKLY" -> RecurrenceFreq.Weekly
        "MONTHLY" -> RecurrenceFreq.Monthly
        "YEARLY" -> RecurrenceFreq.Yearly
        else -> return null
    }

    val interval = parts["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1

    val byDays = parts["BYDAY"]?.let { raw ->
        if (freq != RecurrenceFreq.Weekly) return null
        raw.split(',').map { code ->
            DAY_CODE_TO_WEEKDAY[code.trim().uppercase()] ?: return null
        }.toSet()
    } ?: emptySet()

    val until = parts["UNTIL"]
    val count = parts["COUNT"]
    if (until != null && count != null) return null

    val end = when {
        until != null -> {
            val date = parseBasicDateOrDateTime(until, zone) ?: return null
            RecurrenceEnd.Until(date)
        }
        count != null -> {
            val times = count.toIntOrNull()?.takeIf { it >= 1 } ?: return null
            RecurrenceEnd.Count(times)
        }
        else -> RecurrenceEnd.Never
    }

    return SimpleRecurrence(freq, interval, end, byDays)
}

/**
 * Truncates an RRULE so it ends right before [untilUtcMillis].
 * Used for "Delete this and following occurrences" or "Edit this and following".
 */
fun rruleTruncatedAt(rrule: String, untilUtcMillis: Long): String {
    val kept = rrule.removePrefix("RRULE:")
        .split(';')
        .filter { it.isNotBlank() }
        .filterNot { part ->
            val key = part.substringBefore('=').trim().uppercase()
            key == "UNTIL" || key == "COUNT"
        }
    val untilPart = "UNTIL=" + Instant.ofEpochMilli(untilUtcMillis).toUtcBasic()
    return (kept + untilPart).joinToString(";")
}

/**
 * Returns a human-friendly string for a given RRULE (e.g. "Repeats weekly on Tuesday").
 */
fun recurrenceToHumanReadable(
    rrule: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    if (rrule.isNullOrBlank()) return null
    val simple = parseSimpleRecurrence(rrule, zone) ?: return "Repeats (custom rule)"

    val freqText = when (simple.freq) {
        RecurrenceFreq.Daily -> if (simple.interval == 1) "Daily" else "Every ${simple.interval} days"
        RecurrenceFreq.Weekly -> {
            val base = if (simple.interval == 1) "Weekly" else "Every ${simple.interval} weeks"
            if (simple.byDays.isNotEmpty()) {
                val days = simple.byDays.sortedBy { it.value }
                    .joinToString(", ") { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
                "$base on $days"
            } else {
                base
            }
        }
        RecurrenceFreq.Monthly -> if (simple.interval == 1) "Monthly" else "Every ${simple.interval} months"
        RecurrenceFreq.Yearly -> if (simple.interval == 1) "Yearly" else "Every ${simple.interval} years"
    }

    val endText = when (val e = simple.end) {
        RecurrenceEnd.Never -> ""
        is RecurrenceEnd.Until -> " until ${e.date}"
        is RecurrenceEnd.Count -> ", ${e.times} times"
    }

    return "Repeats $freqText$endText"
}
