package dev.vinny.gsd.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vinny.gsd.GsdApp
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Notification action buttons (spec §11.4): both act through the store —
 * Complete carries full semantics (recurrence spawn, sync enqueue) — and
 * dismiss the notification without opening the app.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as GsdApp).container
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> container.store.setCompleted(taskId, true)
                    ACTION_SNOOZE -> container.store.snooze(taskId, Instant.now().plus(Duration.ofHours(1)))
                }
                context.getSystemService(NotificationManager::class.java).cancel(taskId.hashCode())
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "dev.vinny.gsd.notification.COMPLETE"
        const val ACTION_SNOOZE = "dev.vinny.gsd.notification.SNOOZE"
    }
}
