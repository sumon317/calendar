package com.sumon.calendar.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

enum class CalendarViewMode(val label: String) {
    Month("Month"),
    Week("Week"),
    Day("Day"),
    Agenda("Agenda"),
}

data class CalendarSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isVisibleInSystem: Boolean,
    val canModifyContents: Boolean = true,
    val isLocal: Boolean = false,
)

enum class EventResponse {
    Going,
    Invited,
    Declined,
}

enum class EventStatus {
    Confirmed,
    Tentative,
    Cancelled,
}

enum class Availability {
    Busy,
    Free,
    Tentative,
}

enum class AccessLevel {
    Default,
    Public,
    Private,
    Confidential,
}

enum class AttendeeStatus {
    Accepted,
    Declined,
    Tentative,
    NeedsAction,
    Unknown,
}

enum class AttendeeRelationship {
    Organizer,
    Attendee,
    Performer,
    Speaker,
    None,
}

enum class AttendeeType {
    Required,
    Optional,
    Resource,
    None,
}

data class Attendee(
    val name: String,
    val email: String?,
    val status: AttendeeStatus = AttendeeStatus.Unknown,
    val relationship: AttendeeRelationship = AttendeeRelationship.Attendee,
    val type: AttendeeType = AttendeeType.Required,
)

enum class ReminderMethod {
    Alert,
    Email,
    Sms,
    Alarm,
    Default,
}

data class Reminder(
    val minutes: Int,
    val method: ReminderMethod = ReminderMethod.Alert,
)

data class EventInstance(
    val instanceId: Long,
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val isAllDay: Boolean,
    val color: Int,
    val location: String?,
    val isRecurring: Boolean = false,
    val response: EventResponse = EventResponse.Going,
    val status: EventStatus = EventStatus.Confirmed,
) {
    val isCrossedOut: Boolean
        get() = response == EventResponse.Declined || status == EventStatus.Cancelled

    val isOutlined: Boolean
        get() = !isCrossedOut && (response == EventResponse.Invited || status == EventStatus.Tentative)

    fun startInstant(): Instant = Instant.ofEpochMilli(startEpochMillis)
    fun endInstant(): Instant = Instant.ofEpochMilli(endEpochMillis)

    fun startDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        if (isAllDay) Instant.ofEpochMilli(startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
        else Instant.ofEpochMilli(startEpochMillis).atZone(zone).toLocalDate()

    fun endDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        if (isAllDay) {
            // All-day end date is exclusive next-midnight, so minus 1 millisecond for inclusive day
            Instant.ofEpochMilli(endEpochMillis - 1).atZone(ZoneOffset.UTC).toLocalDate()
        } else {
            val endMillis = if (endEpochMillis > startEpochMillis) endEpochMillis - 1 else startEpochMillis
            Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
        }

    fun spansMultipleDays(zone: ZoneId = ZoneId.systemDefault()): Boolean =
        startDate(zone) != endDate(zone)
}

data class EventDetail(
    val instance: EventInstance,
    val description: String?,
    val organizer: String?,
    val attendees: List<Attendee> = emptyList(),
    val rrule: String? = null,
    val reminders: List<Reminder> = emptyList(),
    val status: EventStatus = EventStatus.Confirmed,
    val availability: Availability = Availability.Busy,
    val accessLevel: AccessLevel = AccessLevel.Default,
    val eventTimezone: String? = null,
    val calendarName: String? = null,
    val canModify: Boolean = true,
    val isException: Boolean = false,
)

enum class RecurringWriteScope {
    ThisEvent,
    ThisAndFollowing,
    AllEvents,
}

data class EventForm(
    val eventId: Long? = null,
    val calendarId: Long,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startDateTime: LocalDateTime,
    val endDateTime: LocalDateTime,
    val isAllDay: Boolean = false,
    val rrule: String? = null,
    val reminders: List<Int> = listOf(10),
    val availability: Availability = Availability.Busy,
    val accessLevel: AccessLevel = AccessLevel.Default,
    val status: EventStatus = EventStatus.Confirmed,
    val color: Int? = null,
    val timezone: String = ZoneId.systemDefault().id,
)
