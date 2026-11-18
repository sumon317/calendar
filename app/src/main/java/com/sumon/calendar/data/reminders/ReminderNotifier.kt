package com.sumon.calendar.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sumon.calendar.MainActivity
import com.sumon.calendar.R

object ReminderNotifier {
    const val CHANNEL_ID = "calendar_reminders"
    const val CHANNEL_NAME = "Calendar Reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Event reminder notifications"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun postNotification(
        context: Context,
        notificationId: Int,
        eventId: Long,
        title: String,
        timeText: String,
        location: String?,
        beginMillis: Long,
    ) {
        if (!canPostNotifications(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_EVENT_ID", eventId)
            putExtra("EXTRA_BEGIN_MILLIS", beginMillis)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snoozeIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_SNOOZE
            putExtra("EXTRA_NOTIFICATION_ID", notificationId)
            putExtra("EXTRA_EVENT_ID", eventId)
            putExtra("EXTRA_TITLE", title)
            putExtra("EXTRA_TIME_TEXT", timeText)
            putExtra("EXTRA_LOCATION", location)
            putExtra("EXTRA_BEGIN_MILLIS", beginMillis)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismissIntent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DISMISS
            putExtra("EXTRA_NOTIFICATION_ID", notificationId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = if (!location.isNullOrBlank()) "$timeText · $location" else timeText

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Calendar Event" })
            .setContentText(contentText)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openPendingIntent)
            .addAction(0, "Snooze (10m)", snoozePendingIntent)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(context)
        try {
            manager.notify(notificationId, notification)
        } catch (_: SecurityException) {}
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId)
    }
}
