package com.offhand.action

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val body = intent.getStringExtra(EXTRA_BODY) ?: return
        val id = intent.getStringExtra(EXTRA_ID) ?: "reminder"

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(manager)

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Reminder")
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        manager.notify(id.hashCode(), notification)
    }

    companion object {
        const val EXTRA_BODY = "body"
        const val EXTRA_ID = "id"
        const val CHANNEL_ID = "reminders"

        fun ensureChannel(manager: NotificationManager) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
    }
}
