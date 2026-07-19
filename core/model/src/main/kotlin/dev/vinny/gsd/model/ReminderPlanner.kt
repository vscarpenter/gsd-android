package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Domain form of the §7.4 notification settings singleton. */
data class ReminderSettings(
    val enabled: Boolean = true,
    val defaultReminderMinutes: Int = 15,
    val soundEnabled: Boolean = true,
    val quietHoursStart: LocalTime? = null,
    val quietHoursEnd: LocalTime? = null,
    val permissionAsked: Boolean = false,
)

/**
 * The §11.1 scheduling decision, kept pure so every rule is JVM-testable.
 * A future snooze *is* the fire time; quiet hours defer to the window's end.
 */
object ReminderPlanner {
    fun fireTime(task: Task, settings: ReminderSettings, now: Instant, zone: ZoneId): Instant? {
        if (!settings.enabled || !task.notificationEnabled) return null
        if (task.completed || task.notificationSent) return null
        val due = task.dueDate ?: return null

        val snoozed = task.snoozedUntil?.takeIf { it.isAfter(now) }
        val base = snoozed
            ?: due.minusSeconds((task.notifyBefore ?: settings.defaultReminderMinutes) * 60L)
        if (!base.isAfter(now)) return null
        return deferPastQuietHours(base, settings, zone)
    }

    private fun deferPastQuietHours(fire: Instant, settings: ReminderSettings, zone: ZoneId): Instant {
        val start = settings.quietHoursStart ?: return fire
        val end = settings.quietHoursEnd ?: return fire
        val local = fire.atZone(zone)
        val time = local.toLocalTime()
        val crossesMidnight = end.isBefore(start)
        val inWindow = if (crossesMidnight) {
            !time.isBefore(start) || time.isBefore(end)
        } else {
            !time.isBefore(start) && time.isBefore(end)
        }
        if (!inWindow) return fire
        val endToday = local.toLocalDate().atTime(end).atZone(zone)
        val deferred = if (endToday.toInstant().isAfter(fire)) endToday else endToday.plusDays(1)
        return deferred.toInstant()
    }
}
