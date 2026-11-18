package com.sumon.calendar.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.CalendarContract
import com.sumon.calendar.data.LocalCalendarRepository
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object ReminderAlarmScheduler {
    private const val ALARM_REQUEST_CODE = 0x4321

    fun scheduleExactAlarm(
        context: Context,
        triggerAtMillis: Long,
        eventId: Long,
        title: String,
        timeText: String,
        location: String?,
        beginMillis: Long,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE_REMINDER
            putExtra("EXTRA_EVENT_ID", eventId)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_TIME_TEXT", timeText)
            putExtra("EXTRA_LOCATION", location)
            putExtra("EXTRA_BEGIN_MILLIS", beginMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    suspend fun rescheduleUpcomingReminders(context: Context) {
        val repo = LocalCalendarRepository(context)
        if (!repo.hasPermissions()) return

        val now = System.currentTimeMillis()
        val horizon = now + 48 * 3600 * 1000L // Next 48 hours

        val disabledIds = repo.getDisabledCalendarIds()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, horizon)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
        )

        data class Candidate(
            val triggerMillis: Long,
            val eventId: Long,
            val title: String,
            val timeText: String,
            val location: String?,
            val beginMillis: Long,
        )

        val candidates = mutableListOf<Candidate>()

        try {
            context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locCol = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val calIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)

                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                val zone = ZoneId.systemDefault()

                while (cursor.moveToNext()) {
                    val calId = cursor.getLong(calIdCol)
                    if (calId in disabledIds) continue

                    val eventId = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Event"
                    val begin = cursor.getLong(beginCol)
                    val end = cursor.getLong(endCol)
                    val isAllDay = cursor.getInt(allDayCol) == 1
                    val location = if (locCol != -1) cursor.getString(locCol) else null

                    // Query reminders for this event
                    val reminderMinutes = queryReminderMinutes(context, eventId) ?: 10 // Default 10 min
                    val triggerMillis = begin - (reminderMinutes * 60 * 1000L)

                    if (triggerMillis > now) {
                        val timeText = if (isAllDay) {
                            "All day"
                        } else {
                            val startLocal = Instant.ofEpochMilli(begin).atZone(zone).toLocalTime()
                            val endLocal = Instant.ofEpochMilli(end).atZone(zone).toLocalTime()
                            "${timeFormatter.format(startLocal)} - ${timeFormatter.format(endLocal)}"
                        }

                        candidates.add(
                            Candidate(
                                triggerMillis = triggerMillis,
                                eventId = eventId,
                                title = title,
                                timeText = timeText,
                                location = location,
                                beginMillis = begin,
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        val earliest = candidates.minByOrNull { it.triggerMillis } ?: return
        scheduleExactAlarm(
            context = context,
            triggerAtMillis = earliest.triggerMillis,
            eventId = earliest.eventId,
            title = earliest.title,
            timeText = earliest.timeText,
            location = earliest.location,
            beginMillis = earliest.beginMillis,
        )
    }

    private fun queryReminderMinutes(context: Context, eventId: Long): Int? {
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
                    return cursor.getInt(col)
                }
            }
        } catch (_: Exception) {}
        return null
    }
}
