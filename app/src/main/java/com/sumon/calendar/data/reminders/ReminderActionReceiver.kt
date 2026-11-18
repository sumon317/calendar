package com.sumon.calendar.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderActionReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_DISMISS = "com.sumon.calendar.ACTION_REMINDER_DISMISS"
        const val ACTION_SNOOZE = "com.sumon.calendar.ACTION_REMINDER_SNOOZE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", -1)
        if (notificationId != -1) {
            ReminderNotifier.cancelNotification(context, notificationId)
        }

        if (intent.action == ACTION_SNOOZE) {
            val eventId = intent.getLongExtra("EXTRA_EVENT_ID", -1L)
            val title = intent.getStringExtra("EXTRA_TITLE").orEmpty()
            val timeText = intent.getStringExtra("EXTRA_TIME_TEXT").orEmpty()
            val location = intent.getStringExtra("EXTRA_LOCATION")
            val beginMillis = intent.getLongExtra("EXTRA_BEGIN_MILLIS", 0L)

            val snoozeTimeMillis = System.currentTimeMillis() + 10 * 60 * 1000L
            ReminderAlarmScheduler.scheduleExactAlarm(
                context = context,
                triggerAtMillis = snoozeTimeMillis,
                eventId = eventId,
                title = title,
                timeText = timeText,
                location = location,
                beginMillis = beginMillis,
            )
        }
    }
}
