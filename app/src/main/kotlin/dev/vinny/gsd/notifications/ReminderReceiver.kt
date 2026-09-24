package dev.vinny.gsd.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.MainActivity
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdColorsLight
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Posts the reminder at alarm time (spec §11.2) with Complete + Snooze actions (§11.4). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(ReminderScheduler.EXTRA_TASK_ID) ?: return
        val container = (context.applicationContext as GsdApp).container
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = container.store.get(taskId) ?: return@launch
                if (task.completed || !task.notificationEnabled) return@launch
                val settings = container.notificationSettingsStore.get()
                val channel = if (settings.soundEnabled) Channels.REMINDERS else Channels.REMINDERS_SILENT

                val tapIntent = PendingIntent.getActivity(
                    context,
                    taskId.hashCode(),
                    tapIntent(context, taskId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                fun action(actionName: String, requestOffset: Int): PendingIntent =
                    PendingIntent.getBroadcast(
                        context,
                        taskId.hashCode() + requestOffset,
                        Intent(context, NotificationActionReceiver::class.java).apply {
                            action = actionName
                            putExtra(ReminderScheduler.EXTRA_TASK_ID, taskId)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )

                val notification = Notification.Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setColor(GsdColorsLight.tint.toArgb())
                    .setCategory(Notification.CATEGORY_REMINDER)
                    .setContentTitle(task.title)
                    .apply { if (task.description.isNotEmpty()) setContentText(task.description) }
                    .setContentIntent(tapIntent)
                    .setAutoCancel(true)
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.notification_complete),
                            action(NotificationActionReceiver.ACTION_COMPLETE, 1),
                        ).build(),
                    )
                    .addAction(
                        Notification.Action.Builder(
                            null,
                            context.getString(R.string.notification_snooze),
                            action(NotificationActionReceiver.ACTION_SNOOZE, 2),
                        ).build(),
                    )
                    .build()
                context.getSystemService(NotificationManager::class.java)
                    .notify(taskId.hashCode(), notification)

                // Device-local reminder state (spec §4.7): bookkeeping, so no restamp and no push
                container.store.markReminderSent(task.id, Instant.now())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Logged, not thrown: a throw here would crash the process from an alarm
                Log.w("GsdReminders", "reminder failed: ${error.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        /** A tap is the gsd://task link (spec §11.2), so the router goes to Matrix first and then opens the task. */
        fun tapIntent(context: Context, taskId: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("gsd://task/$taskId"), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
