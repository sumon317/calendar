package com.sumon.calendar.data

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.edit
import com.sumon.calendar.data.reminders.ReminderAlarmScheduler
import com.sumon.calendar.domain.AccessLevel
import com.sumon.calendar.domain.Attendee
import com.sumon.calendar.domain.AttendeeRelationship
import com.sumon.calendar.domain.AttendeeStatus
import com.sumon.calendar.domain.AttendeeType
import com.sumon.calendar.domain.Availability
import com.sumon.calendar.domain.CalendarSource
import com.sumon.calendar.domain.EventDetail
import com.sumon.calendar.domain.EventForm
import com.sumon.calendar.domain.EventInstance
import com.sumon.calendar.domain.EventResponse
import com.sumon.calendar.domain.EventStatus
import com.sumon.calendar.domain.RecurringWriteScope
import com.sumon.calendar.domain.Reminder
import com.sumon.calendar.domain.ReminderMethod
import com.sumon.calendar.domain.ics.IcsEvent
import com.sumon.calendar.domain.ics.IcsParser
import com.sumon.calendar.domain.ics.IcsWriter
import com.sumon.calendar.domain.rruleTruncatedAt
import com.sumon.calendar.ui.theme.ColorPalettePreset
import com.sumon.calendar.ui.theme.ThemeMode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class LocalCalendarRepository(private val context: Context) {

    fun hasPermissions(): Boolean {
        val readGranted = context.checkSelfPermission(
            Manifest.permission.READ_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        val writeGranted = context.checkSelfPermission(
            Manifest.permission.WRITE_CALENDAR,
        ) == PackageManager.PERMISSION_GRANTED
        return readGranted && writeGranted
    }

    fun observeCalendarChanges(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }
        val cr = context.contentResolver
        try {
            cr.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, observer)
            cr.registerContentObserver(CalendarContract.Calendars.CONTENT_URI, true, observer)
        } catch (_: SecurityException) {}

        trySend(Unit)

        awaitClose {
            try {
                cr.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

    fun getGoogleAccounts(): List<Account> {
        val accounts = mutableSetOf<Account>()
        try {
            val am = AccountManager.get(context)
            val amAccounts = am.getAccountsByType("com.google")
            accounts.addAll(amAccounts)
        } catch (_: Exception) {}

        try {
            val projection = arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
            )
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.ACCOUNT_TYPE} = ?",
                arrayOf("com.google"),
                null,
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol)
                    if (!name.isNullOrBlank()) {
                        accounts.add(Account(name, "com.google"))
                    }
                }
            }
        } catch (_: Exception) {}

        return accounts.toList()
    }

    suspend fun syncGoogleCalendar(targetAccount: Account? = null): Boolean = withContext(Dispatchers.IO) {
        val accounts = if (targetAccount != null) listOf(targetAccount) else getGoogleAccounts()

        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }

        if (accounts.isEmpty()) {
            return@withContext try {
                ContentResolver.requestSync(null, CalendarContract.AUTHORITY, bundle)
                true
            } catch (_: Exception) {
                false
            }
        }

        var anyRequested = false
        for (account in accounts) {
            try {
                if (ContentResolver.getIsSyncable(account, CalendarContract.AUTHORITY) <= 0) {
                    ContentResolver.setIsSyncable(account, CalendarContract.AUTHORITY, 1)
                }
                ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, true)
                ContentResolver.requestSync(account, CalendarContract.AUTHORITY, bundle)
                anyRequested = true
            } catch (_: Exception) {}
        }
        anyRequested
    }

    fun createGoogleLoginIntent(): Intent {
        return try {
            AccountManager.newChooseAccountIntent(
                null, null, arrayOf("com.google"), null, null, null, null,
            )
        } catch (_: Exception) {
            Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
        }
    }

    fun createSyncSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SYNC_SETTINGS).apply {
            putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY))
        }
    }

    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    private val prefs = context.getSharedPreferences("calendar_settings", Context.MODE_PRIVATE)

    fun getDisabledCalendarIds(): Set<Long> {
        val raw = prefs.getStringSet("disabled_calendar_ids", null) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun setCalendarEnabled(calendarId: Long, enabled: Boolean) {
        val current = getDisabledCalendarIds().toMutableSet()
        if (enabled) {
            current.remove(calendarId)
        } else {
            current.add(calendarId)
        }
        prefs.edit {
            putStringSet("disabled_calendar_ids", current.map { it.toString() }.toSet())
        }
    }

    fun setAllCalendarsEnabled(calendars: List<DeviceCalendar>, enabled: Boolean) {
        val newDisabled = if (enabled) {
            emptySet()
        } else {
            calendars.map { it.id.toString() }.toSet()
        }
        prefs.edit {
            putStringSet("disabled_calendar_ids", newDisabled)
        }
    }

    fun getThemeMode(): ThemeMode {
        val saved = prefs.getString("theme_mode", null) ?: return ThemeMode.SYSTEM
        return try {
            ThemeMode.valueOf(saved)
        } catch (_: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit { putString("theme_mode", mode.name) }
    }

    fun getColorPalette(): ColorPalettePreset {
        val saved = prefs.getString("color_palette", null)
        return ColorPalettePreset.fromNameOrDefault(saved)
    }

    fun setColorPalette(palette: ColorPalettePreset) {
        prefs.edit { putString("color_palette", palette.name) }
    }

    suspend fun getCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext emptyList()
        val disabledIds = getDisabledCalendarIds()
        val calendars = mutableListOf<DeviceCalendar>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accNameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accTypeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                val primaryCol = cursor.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val isPrimary = if (primaryCol != -1) cursor.getInt(primaryCol) == 1 else false
                    calendars.add(
                        DeviceCalendar(
                            id = id,
                            displayName = cursor.getString(nameCol) ?: "Calendar",
                            accountName = cursor.getString(accNameCol) ?: "",
                            accountType = cursor.getString(accTypeCol) ?: "",
                            color = cursor.getInt(colorCol),
                            isPrimary = isPrimary,
                            isEnabled = id !in disabledIds,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {}
        calendars
    }

    suspend fun getCalendarSources(): List<CalendarSource> = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext emptyList()
        val disabledIds = getDisabledCalendarIds()
        val sources = mutableListOf<CalendarSource>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars._ID} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accNameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                val accTypeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
                val colorCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                val accessCol = cursor.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val accType = cursor.getString(accTypeCol) ?: ""
                    val access = if (accessCol != -1) cursor.getInt(accessCol) else CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                    sources.add(
                        CalendarSource(
                            id = id,
                            displayName = cursor.getString(nameCol) ?: "Calendar",
                            accountName = cursor.getString(accNameCol) ?: "",
                            accountType = accType,
                            color = cursor.getInt(colorCol),
                            isVisibleInSystem = id !in disabledIds,
                            canModifyContents = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                            isLocal = accType == CalendarContract.ACCOUNT_TYPE_LOCAL,
                        ),
                    )
                }
            }
        } catch (_: Exception) {}
        sources
    }

    suspend fun getOrCreateDefaultCalendarId(): Long = withContext(Dispatchers.IO) {
        val calendars = getCalendars()
        val enabledCalendars = calendars.filter { it.isEnabled }.ifEmpty { calendars }
        val primary = enabledCalendars.firstOrNull { it.isPrimary }
        if (primary != null) return@withContext primary.id

        val googleCal = enabledCalendars.firstOrNull { it.accountType == "com.google" }
        if (googleCal != null) return@withContext googleCal.id

        val local = enabledCalendars.firstOrNull { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
        if (local != null) return@withContext local.id

        if (enabledCalendars.isNotEmpty()) return@withContext enabledCalendars.first().id

        createLocalCalendar()
    }

    private fun createLocalCalendar(): Long {
        val syncUri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "Local")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, "Local")
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, "Local Device Calendar")
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Local Calendar")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF4285F4.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, "Local")
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
        }
        val uri = context.contentResolver.insert(syncUri, values)
        return uri?.lastPathSegment?.toLongOrNull() ?: 1L
    }

    /**
     * Backward-compatible event query for MonthGrid.
     */
    suspend fun getEventsInRange(
        startMillis: Long,
        endMillis: Long,
        calendarIdFilter: Long? = null,
        enabledCalendarIds: Set<Long>? = null,
    ): List<Event> = withContext(Dispatchers.IO) {
        val instances = getEventInstancesInRange(startMillis, endMillis, calendarIdFilter, enabledCalendarIds)
        val systemZone = ZoneId.systemDefault()
        instances.map { inst ->
            val startDate: LocalDate
            val endDate: LocalDate
            val startTime: LocalTime?
            val endTime: LocalTime?

            if (inst.isAllDay) {
                startDate = Instant.ofEpochMilli(inst.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
                val rawEnd = Instant.ofEpochMilli(inst.endEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
                endDate = if (rawEnd.isAfter(startDate)) rawEnd.minusDays(1) else startDate
                startTime = null
                endTime = null
            } else {
                val startZoned = Instant.ofEpochMilli(inst.startEpochMillis).atZone(systemZone)
                val endZoned = Instant.ofEpochMilli(inst.endEpochMillis).atZone(systemZone)
                startDate = startZoned.toLocalDate()
                endDate = endZoned.toLocalDate()
                startTime = startZoned.toLocalTime()
                endTime = endZoned.toLocalTime()
            }

            Event(
                id = inst.eventId,
                calendarId = inst.calendarId,
                title = inst.title,
                location = inst.location,
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                isAllDay = inst.isAllDay,
                color = inst.color,
                calendarName = null,
            )
        }
    }

    /**
     * Returns rich EventInstances for Day, Week, Agenda and Month views.
     */
    suspend fun getEventInstancesInRange(
        startMillis: Long,
        endMillis: Long,
        calendarIdFilter: Long? = null,
        enabledCalendarIds: Set<Long>? = null,
    ): List<EventInstance> = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext emptyList()
        if (calendarIdFilter == null && enabledCalendarIds != null && enabledCalendarIds.isEmpty()) {
            return@withContext emptyList()
        }

        val instances = mutableListOf<EventInstance>()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.RRULE,
        )

        val (selection, selectionArgs) = when {
            calendarIdFilter != null && calendarIdFilter > 0 -> {
                Pair("${CalendarContract.Instances.CALENDAR_ID} = ?", arrayOf(calendarIdFilter.toString()))
            }
            enabledCalendarIds != null -> {
                val placeholders = enabledCalendarIds.joinToString(",") { "?" }
                Pair(
                    "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)",
                    enabledCalendarIds.map { it.toString() }.toTypedArray(),
                )
            }
            else -> Pair(null, null)
        }

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val instIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances._ID)
                val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val locCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val eventColorCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val calColorCol = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val selfStatusCol = cursor.getColumnIndex(CalendarContract.Instances.SELF_ATTENDEE_STATUS)
                val statusCol = cursor.getColumnIndex(CalendarContract.Instances.STATUS)
                val rruleCol = cursor.getColumnIndex(CalendarContract.Instances.RRULE)

                while (cursor.moveToNext()) {
                    val instId = cursor.getLong(instIdCol)
                    val eventId = cursor.getLong(eventIdCol)
                    val title = cursor.getString(titleCol) ?: "(No title)"
                    val location = if (locCol != -1) cursor.getString(locCol) else null
                    val begin = cursor.getLong(beginCol)
                    val end = cursor.getLong(endCol)
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val calId = cursor.getLong(calIdCol)
                    val eventColor = if (eventColorCol != -1) cursor.getInt(eventColorCol) else 0
                    val calColor = if (calColorCol != -1) cursor.getInt(calColorCol) else 0
                    val effectiveColor = if (eventColor != 0) eventColor else if (calColor != 0) calColor else 0xFF4285F4.toInt()

                    val selfStatus = if (selfStatusCol != -1) cursor.getInt(selfStatusCol) else CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED
                    val response = when (selfStatus) {
                        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> EventResponse.Declined
                        CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> EventResponse.Invited
                        else -> EventResponse.Going
                    }

                    val rawStatus = if (statusCol != -1) cursor.getInt(statusCol) else CalendarContract.Events.STATUS_CONFIRMED
                    val status = when (rawStatus) {
                        CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative
                        CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled
                        else -> EventStatus.Confirmed
                    }

                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null

                    instances.add(
                        EventInstance(
                            instanceId = instId,
                            eventId = eventId,
                            calendarId = calId,
                            title = title,
                            startEpochMillis = begin,
                            endEpochMillis = end,
                            isAllDay = isAllDay,
                            color = effectiveColor,
                            location = location,
                            isRecurring = !rrule.isNullOrBlank(),
                            response = response,
                            status = status,
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        instances
    }

    suspend fun queryEventDetail(eventId: Long, beginMillis: Long, endMillis: Long): EventDetail? = withContext(Dispatchers.IO) {
        if (!hasPermissions() || eventId <= 0) return@withContext null

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.ORGANIZER,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.AVAILABILITY,
            CalendarContract.Events.ACCESS_LEVEL,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.ORIGINAL_ID,
        )

        var detail: EventDetail? = null
        try {
            val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.query(eventUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                    val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                    val descCol = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                    val locCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                    val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                    val orgCol = cursor.getColumnIndex(CalendarContract.Events.ORGANIZER)
                    val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                    val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)
                    val availCol = cursor.getColumnIndex(CalendarContract.Events.AVAILABILITY)
                    val accessCol = cursor.getColumnIndex(CalendarContract.Events.ACCESS_LEVEL)
                    val tzCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                    val colorCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                    val origIdCol = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ID)

                    val calId = cursor.getLong(calIdCol)
                    val title = cursor.getString(titleCol) ?: "(No title)"
                    val description = if (descCol != -1) cursor.getString(descCol) else null
                    val location = if (locCol != -1) cursor.getString(locCol) else null
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val organizer = if (orgCol != -1) cursor.getString(orgCol) else null
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null
                    val timezone = if (tzCol != -1) cursor.getString(tzCol) else null
                    val isException = if (origIdCol != -1) !cursor.isNull(origIdCol) else false

                    val rawStatus = if (statusCol != -1) cursor.getInt(statusCol) else CalendarContract.Events.STATUS_CONFIRMED
                    val status = when (rawStatus) {
                        CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative
                        CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled
                        else -> EventStatus.Confirmed
                    }

                    val rawAvail = if (availCol != -1) cursor.getInt(availCol) else CalendarContract.Events.AVAILABILITY_BUSY
                    val availability = when (rawAvail) {
                        CalendarContract.Events.AVAILABILITY_FREE -> Availability.Free
                        CalendarContract.Events.AVAILABILITY_TENTATIVE -> Availability.Tentative
                        else -> Availability.Busy
                    }

                    val rawAccess = if (accessCol != -1) cursor.getInt(accessCol) else CalendarContract.Events.ACCESS_DEFAULT
                    val accessLevel = when (rawAccess) {
                        CalendarContract.Events.ACCESS_PUBLIC -> AccessLevel.Public
                        CalendarContract.Events.ACCESS_PRIVATE -> AccessLevel.Private
                        CalendarContract.Events.ACCESS_CONFIDENTIAL -> AccessLevel.Confidential
                        else -> AccessLevel.Default
                    }

                    // Query calendar info
                    val (calName, calColor, canModify) = queryCalendarInfo(calId)
                    val eventColor = if (colorCol != -1 && !cursor.isNull(colorCol)) cursor.getInt(colorCol) else calColor

                    // Query attendees
                    val attendees = queryAttendees(eventId)
                    // Query reminders
                    val reminders = queryReminders(eventId)

                    val instance = EventInstance(
                        instanceId = 0L,
                        eventId = eventId,
                        calendarId = calId,
                        title = title,
                        startEpochMillis = beginMillis,
                        endEpochMillis = endMillis,
                        isAllDay = isAllDay,
                        color = eventColor,
                        location = location,
                        isRecurring = !rrule.isNullOrBlank(),
                        status = status,
                    )

                    detail = EventDetail(
                        instance = instance,
                        description = description,
                        organizer = organizer,
                        attendees = attendees,
                        rrule = rrule,
                        reminders = reminders,
                        status = status,
                        availability = availability,
                        accessLevel = accessLevel,
                        eventTimezone = timezone,
                        calendarName = calName,
                        canModify = canModify,
                        isException = isException,
                    )
                }
            }
        } catch (_: Exception) {}
        detail
    }

    private fun queryCalendarInfo(calId: Long): Triple<String, Int, Boolean> {
        var name = "Calendar"
        var color = 0xFF4285F4.toInt()
        var canModify = true
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calId)
            val proj = arrayOf(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            )
            context.contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    name = cursor.getString(0) ?: name
                    color = cursor.getInt(1)
                    val access = cursor.getInt(2)
                    canModify = access >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                }
            }
        } catch (_: Exception) {}
        return Triple(name, color, canModify)
    }

    private fun queryAttendees(eventId: Long): List<Attendee> {
        val list = mutableListOf<Attendee>()
        try {
            val proj = arrayOf(
                CalendarContract.Attendees.ATTENDEE_NAME,
                CalendarContract.Attendees.ATTENDEE_EMAIL,
                CalendarContract.Attendees.ATTENDEE_STATUS,
                CalendarContract.Attendees.ATTENDEE_RELATIONSHIP,
                CalendarContract.Attendees.ATTENDEE_TYPE,
            )
            context.contentResolver.query(
                CalendarContract.Attendees.CONTENT_URI,
                proj,
                "${CalendarContract.Attendees.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                null,
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_NAME)
                val emailCol = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_EMAIL)
                val statusCol = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_STATUS)
                val relCol = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_RELATIONSHIP)
                val typeCol = cursor.getColumnIndex(CalendarContract.Attendees.ATTENDEE_TYPE)

                while (cursor.moveToNext()) {
                    val name = if (nameCol != -1) cursor.getString(nameCol) else ""
                    val email = if (emailCol != -1) cursor.getString(emailCol) else null
                    val rawStatus = if (statusCol != -1) cursor.getInt(statusCol) else 0
                    val rawRel = if (relCol != -1) cursor.getInt(relCol) else 0
                    val rawType = if (typeCol != -1) cursor.getInt(typeCol) else 0

                    val status = when (rawStatus) {
                        CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.Accepted
                        CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.Declined
                        CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.Tentative
                        CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.NeedsAction
                        else -> AttendeeStatus.Unknown
                    }

                    val relationship = when (rawRel) {
                        CalendarContract.Attendees.RELATIONSHIP_ORGANIZER -> AttendeeRelationship.Organizer
                        CalendarContract.Attendees.RELATIONSHIP_ATTENDEE -> AttendeeRelationship.Attendee
                        CalendarContract.Attendees.RELATIONSHIP_PERFORMER -> AttendeeRelationship.Performer
                        CalendarContract.Attendees.RELATIONSHIP_SPEAKER -> AttendeeRelationship.Speaker
                        else -> AttendeeRelationship.None
                    }

                    val type = when (rawType) {
                        CalendarContract.Attendees.TYPE_REQUIRED -> AttendeeType.Required
                        CalendarContract.Attendees.TYPE_OPTIONAL -> AttendeeType.Optional
                        CalendarContract.Attendees.TYPE_RESOURCE -> AttendeeType.Resource
                        else -> AttendeeType.None
                    }

                    list.add(
                        Attendee(
                            name = name.ifBlank { email ?: "Guest" },
                            email = email,
                            status = status,
                            relationship = relationship,
                            type = type,
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun queryReminders(eventId: Long): List<Reminder> {
        val list = mutableListOf<Reminder>()
        try {
            val proj = arrayOf(
                CalendarContract.Reminders.MINUTES,
                CalendarContract.Reminders.METHOD,
            )
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                proj,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                "${CalendarContract.Reminders.MINUTES} ASC",
            )?.use { cursor ->
                val minCol = cursor.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
                val methodCol = cursor.getColumnIndexOrThrow(CalendarContract.Reminders.METHOD)
                while (cursor.moveToNext()) {
                    val minutes = cursor.getInt(minCol)
                    val rawMethod = cursor.getInt(methodCol)
                    val method = when (rawMethod) {
                        CalendarContract.Reminders.METHOD_ALERT -> ReminderMethod.Alert
                        CalendarContract.Reminders.METHOD_EMAIL -> ReminderMethod.Email
                        CalendarContract.Reminders.METHOD_SMS -> ReminderMethod.Sms
                        CalendarContract.Reminders.METHOD_ALARM -> ReminderMethod.Alarm
                        else -> ReminderMethod.Default
                    }
                    list.add(Reminder(minutes, method))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun searchEvents(query: String): List<EventInstance> = withContext(Dispatchers.IO) {
        if (!hasPermissions() || query.isBlank()) return@withContext emptyList()
        val trimmed = "%${query.trim()}%"
        val results = mutableListOf<EventInstance>()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.STATUS,
        )

        val selection = "${CalendarContract.Events.TITLE} LIKE ? OR " +
            "${CalendarContract.Events.DESCRIPTION} LIKE ? OR " +
            "${CalendarContract.Events.EVENT_LOCATION} LIKE ?"
        val selectionArgs = arrayOf(trimmed, trimmed, trimmed)

        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} DESC LIMIT 100",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val locCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val colorCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_COLOR)
                val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)

                val disabledIds = getDisabledCalendarIds()

                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(calIdCol)
                    if (calId in disabledIds) continue

                    val eventId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "(No title)"
                    val loc = if (locCol != -1) cursor.getString(locCol) else null
                    val start = cursor.getLong(startCol)
                    val end = if (endCol != -1 && !cursor.isNull(endCol)) cursor.getLong(endCol) else (start + 3600000L)
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val color = if (colorCol != -1 && !cursor.isNull(colorCol)) cursor.getInt(colorCol) else 0xFF4285F4.toInt()
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null
                    val rawStatus = if (statusCol != -1) cursor.getInt(statusCol) else CalendarContract.Events.STATUS_CONFIRMED

                    val status = when (rawStatus) {
                        CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative
                        CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled
                        else -> EventStatus.Confirmed
                    }

                    results.add(
                        EventInstance(
                            instanceId = 0L,
                            eventId = eventId,
                            calendarId = calId,
                            title = title,
                            startEpochMillis = start,
                            endEpochMillis = end,
                            isAllDay = isAllDay,
                            color = color,
                            location = loc,
                            isRecurring = !rrule.isNullOrBlank(),
                            status = status,
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    /**
     * Backward-compatible save event.
     */
    suspend fun saveEvent(event: Event): Long = withContext(Dispatchers.IO) {
        val form = EventForm(
            eventId = if (event.id > 0) event.id else null,
            calendarId = if (event.calendarId > 0) event.calendarId else getOrCreateDefaultCalendarId(),
            title = event.title,
            description = event.description.orEmpty(),
            location = event.location.orEmpty(),
            startDateTime = if (event.isAllDay) event.startDate.atStartOfDay() else event.startDate.atTime(event.startTime ?: LocalTime.of(9, 0)),
            endDateTime = if (event.isAllDay) event.endDate.plusDays(1).atStartOfDay() else event.endDate.atTime(event.endTime ?: LocalTime.of(10, 0)),
            isAllDay = event.isAllDay,
            reminders = listOfNotNull(event.reminderMinutes),
            color = event.color,
        )
        saveEventAdvanced(form, RecurringWriteScope.AllEvents, 0L)
    }

    /**
     * Advanced scoped event write supporting:
     * - One-off events
     * - Recurring series creation & full series updates (AllEvents)
     * - Modifying a single occurrence (ThisEvent) via CONTENT_EXCEPTION_URI
     * - Splitting a series (ThisAndFollowing) via RRULE UNTIL truncation
     */
    suspend fun saveEventAdvanced(
        form: EventForm,
        scope: RecurringWriteScope = RecurringWriteScope.AllEvents,
        occurrenceBeginMillis: Long = 0L,
    ): Long = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext 0L
        val targetCalId = if (form.calendarId > 0) form.calendarId else getOrCreateDefaultCalendarId()
        val zone = ZoneId.systemDefault()

        val startMillis: Long
        val endMillis: Long
        val timezone: String

        if (form.isAllDay) {
            startMillis = form.startDateTime.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val endLocalDate = form.endDateTime.toLocalDate()
            val exclusiveEnd = if (endLocalDate.isAfter(form.startDateTime.toLocalDate())) endLocalDate else form.startDateTime.toLocalDate().plusDays(1)
            endMillis = exclusiveEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            timezone = "UTC"
        } else {
            startMillis = form.startDateTime.atZone(zone).toInstant().toEpochMilli()
            val endZoned = form.endDateTime.atZone(zone)
            endMillis = if (endZoned.toInstant().toEpochMilli() > startMillis) endZoned.toInstant().toEpochMilli() else startMillis + 3600000L
            timezone = zone.id
        }

        val durationString = if (form.isAllDay) {
            "P" + ((endMillis - startMillis) / (86400 * 1000L)).coerceAtLeast(1) + "D"
        } else {
            "P" + ((endMillis - startMillis) / 1000L).coerceAtLeast(60) + "S"
        }

        val eventId: Long

        when {
            // New event creation
            form.eventId == null || form.eventId == 0L -> {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalId)
                    put(CalendarContract.Events.TITLE, form.title.trim())
                    put(CalendarContract.Events.DESCRIPTION, form.description.trim())
                    put(CalendarContract.Events.EVENT_LOCATION, form.location.trim())
                    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    if (form.rrule.isNullOrBlank()) {
                        put(CalendarContract.Events.DTEND, endMillis)
                    } else {
                        put(CalendarContract.Events.RRULE, form.rrule)
                        put(CalendarContract.Events.DURATION, durationString)
                    }
                    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_BUSY)
                    put(CalendarContract.Events.HAS_ALARM, if (form.reminders.isNotEmpty()) 1 else 0)
                    if (form.color != null && form.color != 0) {
                        put(CalendarContract.Events.EVENT_COLOR, form.color)
                    }
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                eventId = uri?.lastPathSegment?.toLongOrNull() ?: 0L
            }

            // Scoped Edit: Only this event (Single occurrence exception)
            scope == RecurringWriteScope.ThisEvent && occurrenceBeginMillis > 0L -> {
                val exceptionUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, form.eventId)
                val values = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, occurrenceBeginMillis)
                    put(CalendarContract.Events.TITLE, form.title.trim())
                    put(CalendarContract.Events.DESCRIPTION, form.description.trim())
                    put(CalendarContract.Events.EVENT_LOCATION, form.location.trim())
                    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    put(CalendarContract.Events.DURATION, durationString)
                    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                }
                val uri = context.contentResolver.insert(exceptionUri, values)
                eventId = uri?.lastPathSegment?.toLongOrNull() ?: form.eventId
            }

            // Scoped Edit: This and following occurrences
            scope == RecurringWriteScope.ThisAndFollowing && occurrenceBeginMillis > 0L -> {
                // 1. Truncate existing series RRULE
                val oldRRule = queryRrule(form.eventId)
                if (!oldRRule.isNullOrBlank()) {
                    val truncated = rruleTruncatedAt(oldRRule, occurrenceBeginMillis - 1)
                    val updateValues = ContentValues().apply {
                        put(CalendarContract.Events.RRULE, truncated)
                    }
                    context.contentResolver.update(
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, form.eventId),
                        updateValues,
                        null,
                        null,
                    )
                }

                // 2. Insert new series starting from this occurrence
                val newValues = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalId)
                    put(CalendarContract.Events.TITLE, form.title.trim())
                    put(CalendarContract.Events.DESCRIPTION, form.description.trim())
                    put(CalendarContract.Events.EVENT_LOCATION, form.location.trim())
                    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    if (form.rrule.isNullOrBlank()) {
                        put(CalendarContract.Events.DTEND, endMillis)
                    } else {
                        put(CalendarContract.Events.RRULE, form.rrule)
                        put(CalendarContract.Events.DURATION, durationString)
                    }
                    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.HAS_ALARM, if (form.reminders.isNotEmpty()) 1 else 0)
                }
                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, newValues)
                eventId = uri?.lastPathSegment?.toLongOrNull() ?: form.eventId
            }

            // Scope: All events (Master update)
            else -> {
                eventId = form.eventId
                val values = ContentValues().apply {
                    put(CalendarContract.Events.CALENDAR_ID, targetCalId)
                    put(CalendarContract.Events.TITLE, form.title.trim())
                    put(CalendarContract.Events.DESCRIPTION, form.description.trim())
                    put(CalendarContract.Events.EVENT_LOCATION, form.location.trim())
                    put(CalendarContract.Events.ALL_DAY, if (form.isAllDay) 1 else 0)
                    put(CalendarContract.Events.DTSTART, startMillis)
                    if (form.rrule.isNullOrBlank()) {
                        put(CalendarContract.Events.DTEND, endMillis)
                        putNull(CalendarContract.Events.RRULE)
                        putNull(CalendarContract.Events.DURATION)
                    } else {
                        putNull(CalendarContract.Events.DTEND)
                        put(CalendarContract.Events.RRULE, form.rrule)
                        put(CalendarContract.Events.DURATION, durationString)
                    }
                    put(CalendarContract.Events.EVENT_TIMEZONE, timezone)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
                    put(CalendarContract.Events.HAS_ALARM, if (form.reminders.isNotEmpty()) 1 else 0)
                    if (form.color != null && form.color != 0) {
                        put(CalendarContract.Events.EVENT_COLOR, form.color)
                    }
                }
                context.contentResolver.update(
                    ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                    values,
                    null,
                    null,
                )

                // Replace reminders
                context.contentResolver.delete(
                    CalendarContract.Reminders.CONTENT_URI,
                    "${CalendarContract.Reminders.EVENT_ID} = ?",
                    arrayOf(eventId.toString()),
                )
            }
        }

        // Insert new reminders
        if (eventId > 0 && form.reminders.isNotEmpty()) {
            for (min in form.reminders.distinct()) {
                val rVal = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, min)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, rVal)
            }
        }

        // Arm next alarm and trigger sync
        ReminderAlarmScheduler.rescheduleUpcomingReminders(context)
        syncGoogleCalendar()

        eventId
    }

    private fun queryRrule(eventId: Long): String? {
        try {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            context.contentResolver.query(uri, arrayOf(CalendarContract.Events.RRULE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(0)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        deleteEventScoped(eventId, RecurringWriteScope.AllEvents, 0L)
    }

    suspend fun deleteEventScoped(
        eventId: Long,
        scope: RecurringWriteScope,
        occurrenceBeginMillis: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermissions() || eventId <= 0) return@withContext false

        val success: Boolean = when {
            scope == RecurringWriteScope.ThisEvent && occurrenceBeginMillis > 0L -> {
                val exceptionUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_EXCEPTION_URI, eventId)
                val values = ContentValues().apply {
                    put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, occurrenceBeginMillis)
                    put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                }
                val inserted = context.contentResolver.insert(exceptionUri, values)
                inserted != null
            }
            scope == RecurringWriteScope.ThisAndFollowing && occurrenceBeginMillis > 0L -> {
                val oldRRule = queryRrule(eventId)
                if (!oldRRule.isNullOrBlank()) {
                    val truncated = rruleTruncatedAt(oldRRule, occurrenceBeginMillis - 1)
                    val updateValues = ContentValues().apply {
                        put(CalendarContract.Events.RRULE, truncated)
                    }
                    val updated = context.contentResolver.update(
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                        updateValues,
                        null,
                        null,
                    )
                    updated > 0
                } else {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                    context.contentResolver.delete(uri, null, null) > 0
                }
            }
            else -> {
                val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
                context.contentResolver.delete(uri, null, null) > 0
            }
        }

        if (success) {
            ReminderAlarmScheduler.rescheduleUpcomingReminders(context)
            syncGoogleCalendar()
        }
        success
    }

    suspend fun exportCalendarIcs(calendarIds: Set<Long>? = null): String = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext ""
        val targetIds = calendarIds ?: getCalendars().filter { it.isEnabled }.map { it.id }.toSet()
        if (targetIds.isEmpty()) return@withContext ""

        val placeholders = targetIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Events.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = targetIds.map { it.toString() }.toTypedArray()

        val proj = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.AVAILABILITY,
        )

        val icsEvents = mutableListOf<IcsEvent>()
        try {
            context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                proj,
                selection,
                selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val descCol = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
                val locCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val startCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endCol = cursor.getColumnIndex(CalendarContract.Events.DTEND)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val rruleCol = cursor.getColumnIndex(CalendarContract.Events.RRULE)
                val tzCol = cursor.getColumnIndex(CalendarContract.Events.EVENT_TIMEZONE)
                val statusCol = cursor.getColumnIndex(CalendarContract.Events.STATUS)
                val availCol = cursor.getColumnIndex(CalendarContract.Events.AVAILABILITY)

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Event"
                    val desc = if (descCol != -1) cursor.getString(descCol) else null
                    val loc = if (locCol != -1) cursor.getString(locCol) else null
                    val start = cursor.getLong(startCol)
                    val end = if (endCol != -1 && !cursor.isNull(endCol)) cursor.getLong(endCol) else (start + 3600000L)
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val rrule = if (rruleCol != -1) cursor.getString(rruleCol) else null
                    val tz = if (tzCol != -1 && !cursor.isNull(tzCol)) cursor.getString(tzCol) else ZoneId.systemDefault().id
                    val rawStatus = if (statusCol != -1) cursor.getInt(statusCol) else CalendarContract.Events.STATUS_CONFIRMED
                    val rawAvail = if (availCol != -1) cursor.getInt(availCol) else CalendarContract.Events.AVAILABILITY_BUSY

                    val status = when (rawStatus) {
                        CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.Tentative
                        CalendarContract.Events.STATUS_CANCELED -> EventStatus.Cancelled
                        else -> EventStatus.Confirmed
                    }

                    val avail = when (rawAvail) {
                        CalendarContract.Events.AVAILABILITY_FREE -> Availability.Free
                        CalendarContract.Events.AVAILABILITY_TENTATIVE -> Availability.Tentative
                        else -> Availability.Busy
                    }

                    val reminderMins = queryReminders(eventId).map { it.minutes }

                    icsEvents.add(
                        IcsEvent(
                            uid = "${eventId}-${UUID.randomUUID()}@sumon.calendar",
                            summary = title,
                            startEpochMillis = start,
                            endEpochMillis = end,
                            isAllDay = isAllDay,
                            zoneId = tz,
                            recurrenceRule = rrule,
                            location = loc,
                            description = desc,
                            reminderMinutes = reminderMins,
                            status = status,
                            availability = avail,
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        val writer = IcsWriter()
        writer.writeCalendar(icsEvents)
    }

    suspend fun importIcs(icsContent: String, targetCalendarId: Long): Int = withContext(Dispatchers.IO) {
        if (!hasPermissions() || icsContent.isBlank()) return@withContext 0
        val parser = IcsParser()
        val parsed = parser.parse(icsContent)
        if (parsed.isEmpty()) return@withContext 0

        var count = 0
        for (event in parsed) {
            val startLocal = Instant.ofEpochMilli(event.startEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            val endLocal = Instant.ofEpochMilli(event.endEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()

            val form = EventForm(
                calendarId = targetCalendarId,
                title = event.summary,
                description = event.description.orEmpty(),
                location = event.location.orEmpty(),
                startDateTime = startLocal,
                endDateTime = endLocal,
                isAllDay = event.isAllDay,
                rrule = event.recurrenceRule,
                reminders = event.reminderMinutes,
                status = event.status,
                availability = event.availability,
            )
            val id = saveEventAdvanced(form, RecurringWriteScope.AllEvents, 0L)
            if (id > 0) count++
        }
        count
    }

    suspend fun getReminderMinutes(eventId: Long): Int? = withContext(Dispatchers.IO) {
        if (!hasPermissions() || eventId <= 0) return@withContext null
        try {
            context.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI,
                arrayOf(CalendarContract.Reminders.MINUTES),
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(eventId.toString()),
                "${CalendarContract.Reminders.MINUTES} ASC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val col = cursor.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES)
                    return@withContext cursor.getInt(col)
                }
            }
        } catch (_: Exception) {}
        null
    }

    companion object {
        fun monthToMillisRange(month: YearMonth): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val startMillis = month.atDay(1).minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = month.atEndOfMonth().plusDays(7).atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
            return Pair(startMillis, endMillis)
        }

        fun dayToMillisRange(day: LocalDate): Pair<Long, Long> {
            val zone = ZoneId.systemDefault()
            val startMillis = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val endMillis = day.atTime(LocalTime.MAX).atZone(zone).toInstant().toEpochMilli()
            return Pair(startMillis, endMillis)
        }
    }
}
