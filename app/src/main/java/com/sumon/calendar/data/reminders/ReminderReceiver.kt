package com.sumon.calendar.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE_REMINDER = "com.sumon.calendar.ACTION_FIRE_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FIRE_REMINDER) {
            val eventId = intent.getLongExtra("EXTRA_EVENT_ID", -1L)
            val title = intent.getStringExtra("EXTRA_TITLE").orEmpty()
            val timeText = intent.getStringExtra("EXTRA_TIME_TEXT").orEmpty()
            val location = intent.getStringExtra("EXTRA_LOCATION")
            val beginMillis = intent.getLongExtra("EXTRA_BEGIN_MILLIS", 0L)
            val notificationId = (eventId.hashCode() xor beginMillis.hashCode())

            ReminderNotifier.postNotification(
                context = context,
                notificationId = notificationId,
                eventId = eventId,
                title = title,
                timeText = timeText,
                location = location,
                beginMillis = beginMillis,
            )

            // Reschedule subsequent upcoming reminders in background
            CoroutineScope(Dispatchers.IO).launch {
                ReminderAlarmScheduler.rescheduleUpcomingReminders(context)
            }
        }
    }
}
