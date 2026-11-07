package dev.vinny.gsd.notifications

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.vinny.gsd.GsdApp
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reschedules the widget's midnight alarm and every reminder from the store when the system changes something a fire time depends
 * on (spec §11.2): alarms die on reboot and on an update, a granted exact-alarm permission turns
 * inexact alarms exact, and a new time zone or clock moves quiet-hours deferrals.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val container = (context.applicationContext as GsdApp).container
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The widget's midnight alarm first: a reboot or a new time zone clears or moves it
                dev.vinny.gsd.widget.WidgetMidnight.schedule(context)
                container.reminderScheduler.resyncAll(
                    container.store.observeTasks().first(),
                    container.notificationSettingsStore.get(),
                    Instant.now(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Logged, not thrown: a throw here would crash the process from a system broadcast
                Log.w("GsdReminders", "reschedule failed: ${error.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }
}

/** Every broadcast the manifest routes here. Each one can change when a reminder should fire. */
internal val RESCHEDULE_ACTIONS = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
    Intent.ACTION_TIME_CHANGED,
)
