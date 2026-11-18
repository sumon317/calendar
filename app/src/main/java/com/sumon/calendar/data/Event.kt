package com.sumon.calendar.data

import java.time.LocalDate
import java.time.LocalTime

/**
 * Rich domain model for calendar events.
 * Fully represents events synced with the Android Calendar Provider (CalendarContract)
 * while maintaining compatibility with date/time properties.
 */
data class Event(
    val id: Long = 0,
    val calendarId: Long = 0,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate = startDate,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val isAllDay: Boolean = (startTime == null),
    val color: Int? = null,
    val calendarName: String? = null,
    val reminderMinutes: Int? = null,
) {
    /** Backward compatibility accessor for single-day date. */
    val date: LocalDate get() = startDate

    /** Backward compatibility accessor for start time. */
    val time: LocalTime? get() = startTime

    /** Convenience constructor matching the previous minimal Event shape. */
    constructor(
        id: Long = 0,
        title: String,
        date: LocalDate,
        time: LocalTime? = null,
    ) : this(
        id = id,
        calendarId = 0,
        title = title,
        description = null,
        location = null,
        startDate = date,
        endDate = date,
        startTime = time,
        endTime = time?.plusHours(1),
        isAllDay = (time == null),
        color = null,
        calendarName = null,
        reminderMinutes = null,
    )
}


