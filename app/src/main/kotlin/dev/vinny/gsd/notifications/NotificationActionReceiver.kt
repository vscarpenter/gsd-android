package dev.vinny.gsd.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.store.TaskStore
import java.time.Duration
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
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
                if (runNotificationAction(intent.action, taskId, container.store, Instant.now())) {
                    context.getSystemService(NotificationManager::class.java).cancel(taskId.hashCode())
                }
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

/**
 * Runs a reminder's Complete or Snooze through the store, and says whether the write went through.
 * A failure is logged and never thrown, because an exception here would crash the process from a
 * notification button (review follow-up S6). The caller leaves the notification up, so the user
 * can try again or open the task.
 */
internal suspend fun runNotificationAction(action: String?, taskId: String, store: TaskStore, now: Instant): Boolean =
    try {
        when (action) {
            NotificationActionReceiver.ACTION_COMPLETE -> store.setCompleted(taskId, true)
            NotificationActionReceiver.ACTION_SNOOZE -> store.snooze(taskId, now.plus(Duration.ofHours(1)))
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w("GsdReminders", "notification action failed: ${error.javaClass.simpleName}")
        false
    }
