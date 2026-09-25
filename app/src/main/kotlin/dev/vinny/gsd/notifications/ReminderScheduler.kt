package dev.vinny.gsd.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.vinny.gsd.model.ReminderPlanner
import dev.vinny.gsd.model.ReminderSettings
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.NotificationSettingsStore
import dev.vinny.gsd.store.TaskStore
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * AlarmManager wrapper (spec §11.2): exact when permitted, inexact fallback —
 * never a silent failure (Settings surfaces the degraded state). One stable
 * PendingIntent identity per task so reschedules replace, never stack.
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    private fun pendingIntent(taskId: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_TASK_ID, taskId)
            data = android.net.Uri.parse("gsd://reminder/task-$taskId")
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(taskId: String, fireAt: Instant) {
        val pending = pendingIntent(taskId)
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pending)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt.toEpochMilli(), pending)
        }
    }

    fun cancel(taskId: String) {
        alarmManager.cancel(pendingIntent(taskId))
    }

    fun resyncAll(tasks: List<Task>, settings: ReminderSettings, now: Instant) {
        val planned = ReminderPlanner.soonest(tasks, settings, now, ZoneId.systemDefault(), MAX_SCHEDULED_REMINDERS)
        tasks.forEach { task ->
            val fireAt = planned[task.id]
            if (fireAt != null) schedule(task.id, fireAt) else cancel(task.id)
        }
    }

    companion object {
        const val ACTION_REMINDER = "dev.vinny.gsd.REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"

        /** Below the platform's 500 concurrent alarms per app, which throws when passed. */
        const val MAX_SCHEDULED_REMINDERS = 400
    }
}

/**
 * Debounced full resync off the store's change stream (spec §11.1): remote
 * pulls and SSE writes bypass local mutation hooks, so reminders follow the
 * data, not the code path that changed it.
 */
@OptIn(FlowPreview::class)
class ReminderResync(
    private val taskStore: TaskStore,
    private val settingsStore: NotificationSettingsStore,
    private val scheduler: ReminderScheduler,
) {
    private companion object {
        const val TAG = "GsdReminders"
    }

    fun start(scope: CoroutineScope) {
        combine(taskStore.observeTasks(), settingsStore.observe()) { tasks, settings -> tasks to settings }
            .debounce(1_000)
            .onEach { (tasks, settings) ->
                // One failed resync must not end the stream, or no reminder would change again until a restart.
                runCatching { scheduler.resyncAll(tasks, settings, Instant.now()) }
                    .onFailure { Log.w(TAG, "reminder resync failed: ${it.javaClass.simpleName}") }
            }
            .launchIn(scope)
    }
}
