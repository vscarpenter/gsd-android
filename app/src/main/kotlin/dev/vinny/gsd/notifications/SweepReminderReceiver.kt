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
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal const val SWEEP_NOTIFICATION_ID = 0x5EEB

/**
 * The weekly sweep reminder (spec-weekly-sweep.md §6). Its text is fixed and carries no count, no task title,
 * and no task content. It fires whether or not there are cards, and whether or not this week was swept. Quiet
 * hours don't defer it, because the user chose the time.
 */
class SweepReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_SWEEP) return
        val container = (context.applicationContext as GsdApp).container
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = container.prefs.sweepReminder.first()
                if (reminder.enabled) postSweepNotification(context, container.notificationSettingsStore.get().soundEnabled)
                // The next occurrence, strictly after now, so this alarm repeats weekly
                val clock = Clock.systemDefaultZone()
                container.reminderScheduler.syncSweep(reminder, clock.instant(), clock.zone)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Logged, not thrown: a throw here would crash the process from an alarm
                Log.w("GsdReminders", "sweep reminder failed: ${error.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }
}

internal fun postSweepNotification(context: Context, sound: Boolean) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (!manager.areNotificationsEnabled()) return
    val tap = PendingIntent.getActivity(
        context,
        SWEEP_NOTIFICATION_ID,
        Intent(Intent.ACTION_VIEW, Uri.parse("gsd://sweep"), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(context, if (sound) Channels.REMINDERS else Channels.REMINDERS_SILENT)
        .setSmallIcon(R.drawable.ic_notification)
        .setColor(GsdColorsLight.tint.toArgb())
        .setCategory(Notification.CATEGORY_REMINDER)
        .setContentTitle(context.getString(R.string.sweep_notification_title))
        .setContentText(context.getString(R.string.sweep_notification_text))
        .setContentIntent(tap)
        .setAutoCancel(true)
        .build()
    manager.notify(SWEEP_NOTIFICATION_ID, notification)
}
