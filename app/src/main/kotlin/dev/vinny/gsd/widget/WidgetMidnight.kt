package dev.vinny.gsd.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.glance.appwidget.updateAll
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The start of the local day after [now]. A day with a clock change is still one local day. */
internal fun nextLocalMidnight(now: Instant, zone: ZoneId): Instant =
    now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()

/**
 * One inexact alarm at the next local midnight redraws the widget, because the widget has no timer of its
 * own (`updatePeriodMillis` is 0) and a snapshot written today would show today's picks tomorrow
 * (spec-top-three.md §9). Its own action keeps it clear of the task reminders' alarms.
 */
object WidgetMidnight {
    const val ACTION = "dev.vinny.gsd.WIDGET_MIDNIGHT"

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetMidnightReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Replaces any earlier alarm, so repeated calls never stack. */
    fun schedule(context: Context, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, nextLocalMidnight(now, zone).toEpochMilli(), pendingIntent(context))
    }
}

/** Redraws the widget when the alarm fires, then sets the next one. */
class WidgetMidnightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetMidnight.ACTION) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                GsdWidget().updateAll(context)
                WidgetMidnight.schedule(context)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Logged, not thrown: a throw here would crash the process from an alarm
                Log.w("GsdWidget", "midnight redraw failed: ${error.javaClass.simpleName}")
            } finally {
                result.finish()
            }
        }
    }
}
