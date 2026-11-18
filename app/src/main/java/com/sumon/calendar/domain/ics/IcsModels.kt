package com.sumon.calendar.domain.ics

import com.sumon.calendar.domain.Availability
import com.sumon.calendar.domain.EventStatus

data class IcsEvent(
    val uid: String,
    val summary: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val zoneId: String,
    val recurrenceRule: String? = null,
    val location: String? = null,
    val description: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val status: EventStatus = EventStatus.Confirmed,
    val availability: Availability = Availability.Busy,
    val calendarName: String? = null,
    val color: Int? = null,
)

data class ParsedIcsEvent(
    val uid: String?,
    val summary: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val zoneId: String,
    val recurrenceRule: String? = null,
    val location: String? = null,
    val description: String? = null,
    val reminderMinutes: List<Int> = emptyList(),
    val status: EventStatus = EventStatus.Confirmed,
    val availability: Availability = Availability.Busy,
    val calendarName: String? = null,
    val color: Int? = null,
)
