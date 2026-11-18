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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Repository connecting directly to the Android System Calendar Provider (CalendarContract)
 * and managing Google Calendar synchronization.
 *
 * Provides two-way synchronization:
 * 1. App -> Device & Google Account: Writes are directly made to CalendarContract and manual
 *    sync is expedited via ContentResolver.requestSync().
 * 2. Device & Google Account -> App: ContentObserver listens to CalendarContract changes triggered
 *    by Google Sync Adapter or other apps, immediately pushing updates to the UI.
 */
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

    /**
     * Observes real-time changes to the local Android Calendar database.
     * Emits whenever an event or calendar is inserted, updated, or deleted
     * by this app, another app, or Google's sync adapter.
     */
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
        } catch (_: SecurityException) {
            // Permission not yet granted; will register once granted
        }

        trySend(Unit)

        awaitClose {
            try {
                cr.unregisterContentObserver(observer)
            } catch (_: Exception) {}
        }
    }

    /**
     * Retrieves all Google accounts associated with the device or local calendars.
     */
    fun getGoogleAccounts(): List<Account> {
        val accounts = mutableSetOf<Account>()
        // 1. From AccountManager
        try {
            val am = AccountManager.get(context)
            val amAccounts = am.getAccountsByType("com.google")
            accounts.addAll(amAccounts)
        } catch (_: Exception) {}

        // 2. From CalendarProvider's Calendars table (reliable under READ_CALENDAR permissions)
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

    /**
     * Forces an expedited manual sync with Google Calendar servers.
     * Ensures auto-sync and syncable status are active for Google accounts.
     */
    suspend fun syncGoogleCalendar(targetAccount: Account? = null): Boolean = withContext(Dispatchers.IO) {
        val accounts = if (targetAccount != null) listOf(targetAccount) else getGoogleAccounts()

        val bundle = Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }

        if (accounts.isEmpty()) {
            try {
                ContentResolver.requestSync(null, CalendarContract.AUTHORITY, bundle)
                return@withContext true
            } catch (_: Exception) {
                return@withContext false
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

    /**
     * Creates an Intent to choose an existing Google account or add a new one.
     */
    fun createGoogleLoginIntent(): Intent {
        return try {
            AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                null,
                null,
                null,
                null,
            )
        } catch (_: Exception) {
            Intent(Settings.ACTION_ADD_ACCOUNT).apply {
                putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf("com.google"))
            }
        }
    }

    /**
     * Creates an Intent opening system account sync settings.
     */
    fun createSyncSettingsIntent(): Intent {
        return Intent(Settings.ACTION_SYNC_SETTINGS).apply {
            putExtra(Settings.EXTRA_AUTHORITIES, arrayOf(CalendarContract.AUTHORITY))
        }
    }

    /**
     * Creates an Intent opening this app's details settings page in system settings.
     */
    fun createAppSettingsIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    }

    /** Returns all available calendars on the device. */
    suspend fun getCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext emptyList()
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
                    val isPrimary = if (primaryCol != -1) cursor.getInt(primaryCol) == 1 else false
                    calendars.add(
                        DeviceCalendar(
                            id = cursor.getLong(idCol),
                            displayName = cursor.getString(nameCol) ?: "Calendar",
                            accountName = cursor.getString(accNameCol) ?: "",
                            accountType = cursor.getString(accTypeCol) ?: "",
                            color = cursor.getInt(colorCol),
                            isPrimary = isPrimary,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Handled via permission state
        }
        calendars
    }

    /**
     * Resolves the default calendar ID.
     */
    suspend fun getOrCreateDefaultCalendarId(): Long = withContext(Dispatchers.IO) {
        val calendars = getCalendars()
        val primary = calendars.firstOrNull { it.isPrimary }
        if (primary != null) return@withContext primary.id

        val googleCal = calendars.firstOrNull { it.accountType == "com.google" }
        if (googleCal != null) return@withContext googleCal.id

        val local = calendars.firstOrNull { it.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL }
        if (local != null) return@withContext local.id

        if (calendars.isNotEmpty()) return@withContext calendars.first().id

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
     * Queries expanded instances for recurring and one-off events in a given date range.
     */
    suspend fun getEventsInRange(
        startMillis: Long,
        endMillis: Long,
        calendarIdFilter: Long? = null,
    ): List<Event> = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext emptyList()
        val events = mutableListOf<Event>()

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, startMillis)
        ContentUris.appendId(builder, endMillis)

        val projection = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_COLOR,
            CalendarContract.Instances.CALENDAR_COLOR,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.EVENT_TIMEZONE,
        )

        val selection = if (calendarIdFilter != null && calendarIdFilter > 0) {
            "${CalendarContract.Instances.CALENDAR_ID} = ?"
        } else null
        val selectionArgs = if (calendarIdFilter != null && calendarIdFilter > 0) {
            arrayOf(calendarIdFilter.toString())
        } else null

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val descCol = cursor.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val locCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                val eventColorCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_COLOR)
                val calColorCol = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_COLOR)
                val calNameCol = cursor.getColumnIndex(CalendarContract.Instances.CALENDAR_DISPLAY_NAME)

                val systemZone = ZoneId.systemDefault()

                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(eventIdCol)
                    val title = cursor.getString(titleCol) ?: "(No title)"
                    val description = if (descCol != -1) cursor.getString(descCol) else null
                    val location = if (locCol != -1) cursor.getString(locCol) else null
                    val begin = cursor.getLong(beginCol)
                    val end = cursor.getLong(endCol)
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val calId = cursor.getLong(calIdCol)
                    val eventColor = if (eventColorCol != -1) cursor.getInt(eventColorCol) else 0
                    val calColor = if (calColorCol != -1) cursor.getInt(calColorCol) else 0
                    val effectiveColor = if (eventColor != 0) eventColor else if (calColor != 0) calColor else null
                    val calName = if (calNameCol != -1) cursor.getString(calNameCol) else null

                    val startDate: LocalDate
                    val endDate: LocalDate
                    val startTime: LocalTime?
                    val endTime: LocalTime?

                    if (isAllDay) {
                        startDate = Instant.ofEpochMilli(begin).atZone(ZoneOffset.UTC).toLocalDate()
                        val rawEnd = Instant.ofEpochMilli(end).atZone(ZoneOffset.UTC).toLocalDate()
                        endDate = if (rawEnd.isAfter(startDate)) rawEnd.minusDays(1) else startDate
                        startTime = null
                        endTime = null
                    } else {
                        val startZoned = Instant.ofEpochMilli(begin).atZone(systemZone)
                        val endZoned = Instant.ofEpochMilli(end).atZone(systemZone)
                        startDate = startZoned.toLocalDate()
                        endDate = endZoned.toLocalDate()
                        startTime = startZoned.toLocalTime()
                        endTime = endZoned.toLocalTime()
                    }

                    events.add(
                        Event(
                            id = eventId,
                            calendarId = calId,
                            title = title,
                            description = description,
                            location = location,
                            startDate = startDate,
                            endDate = endDate,
                            startTime = startTime,
                            endTime = endTime,
                            isAllDay = isAllDay,
                            color = effectiveColor,
                            calendarName = calName,
                            reminderMinutes = null,
                        ),
                    )
                }
            }
        } catch (_: SecurityException) {
            // Empty list
        }
        events
    }

    /**
     * Retrieves reminder minutes for an event (e.g. 10 minutes before).
     */
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

    /**
     * Saves an event directly to the Android System Calendar Provider.
     * Inserts new events or updates existing ones, including reminders,
     * and triggers a sync with Google Calendar so changes sync immediately.
     */
    suspend fun saveEvent(event: Event): Long = withContext(Dispatchers.IO) {
        if (!hasPermissions()) return@withContext 0L
        val targetCalId = if (event.calendarId > 0) event.calendarId else getOrCreateDefaultCalendarId()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, targetCalId)
            put(CalendarContract.Events.TITLE, event.title.trim())
            put(CalendarContract.Events.DESCRIPTION, event.description?.trim().orEmpty())
            put(CalendarContract.Events.EVENT_LOCATION, event.location?.trim().orEmpty())

            if (event.isAllDay) {
                put(CalendarContract.Events.ALL_DAY, 1)
                val startUtc = event.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                val endUtc = event.endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                put(CalendarContract.Events.DTSTART, startUtc)
                put(CalendarContract.Events.DTEND, endUtc)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
            } else {
                put(CalendarContract.Events.ALL_DAY, 0)
                val startTime = event.startTime ?: LocalTime.of(9, 0)
                val endTime = event.endTime ?: startTime.plusHours(1)
                val startZoned = event.startDate.atTime(startTime).atZone(ZoneId.systemDefault())
                val endZoned = event.endDate.atTime(endTime).atZone(ZoneId.systemDefault())
                put(CalendarContract.Events.DTSTART, startZoned.toInstant().toEpochMilli())
                put(CalendarContract.Events.DTEND, endZoned.toInstant().toEpochMilli())
                put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            }

            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            put(CalendarContract.Events.HAS_ALARM, if (event.reminderMinutes != null) 1 else 0)
        }

        val eventId: Long
        if (event.id == 0L) {
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            eventId = uri?.lastPathSegment?.toLongOrNull() ?: 0L
        } else {
            eventId = event.id
            val updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id)
            context.contentResolver.update(updateUri, values, null, null)

            // Remove existing reminders to replace them cleanly
            context.contentResolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(event.id.toString()),
            )
        }

        if (eventId > 0 && event.reminderMinutes != null && event.reminderMinutes >= 0) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, event.reminderMinutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }

        // Trigger Google Calendar sync to upload changes immediately
        syncGoogleCalendar()

        eventId
    }

    /**
     * Deletes an event by its ID from CalendarContract.Events,
     * and triggers a sync with Google Calendar so changes sync immediately.
     */
    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasPermissions() || eventId <= 0) return@withContext false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rows = context.contentResolver.delete(uri, null, null)
        if (rows > 0) {
            syncGoogleCalendar()
            true
        } else {
            false
        }
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
