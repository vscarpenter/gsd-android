package dev.vinny.gsd.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dev.vinny.gsd.R

/**
 * Two channels (spec §11.2): channel sound is user-owned on Android, so the
 * in-app Sound toggle honestly maps to picking the silent channel at post time.
 */
object Channels {
    const val REMINDERS = "reminders"
    const val REMINDERS_SILENT = "reminders_silent"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS,
                context.getString(R.string.channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                REMINDERS_SILENT,
                context.getString(R.string.channel_reminders_silent),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }
}
