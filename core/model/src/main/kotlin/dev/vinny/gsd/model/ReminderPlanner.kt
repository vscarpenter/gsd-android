package dev.vinny.gsd.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

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
 * Stored or imported quiet hours, "HH:mm" each. Both times parse, or quiet hours are off: one bad
 * value used to throw on every settings read (review, "Also worth a look").
 */
object QuietHours {
    fun parse(start: String?, end: String?): Pair<LocalTime?, LocalTime?> {
        val from = start?.let(::timeOrNull)
        val until = end?.let(::timeOrNull)
        return if (from != null && until != null) from to until else null to null
    }

    private fun timeOrNull(raw: String): LocalTime? =
        try {
            LocalTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
}

/**
 * The §11.1 scheduling decision, kept pure so every rule is JVM-testable.
 * A future snooze *is* the fire time; quiet hours defer to the window's end.
 */
object ReminderPlanner {
    fun fireTime(task: Task, settings: ReminderSettings, now: Instant, zone: ZoneId): Instant? {
        if (!settings.enabled || !task.notificationEnabled) return null
        if (task.completed) return null
        val due = task.dueDate ?: return null

        val snoozed = task.snoozedUntil?.takeIf { it.isAfter(now) }
        // A future snooze asks for another reminder, even after the first one posted (iOS does the same).
        if (task.notificationSent && snoozed == null) return null
        val base = snoozed
            ?: due.minusSeconds((task.notifyBefore ?: settings.defaultReminderMinutes) * 60L)
        if (!base.isAfter(now)) return null
        return deferPastQuietHours(base, settings, zone)
    }

    /**
     * The reminders that get an alarm now: task id to fire time, the soonest [limit] of them. Android
     * refuses more than 500 concurrent alarms per app, so a large import must not ask for one per task.
     * The rest get theirs on a later resync, and every store change runs one, a posted reminder included.
     * Equal fire times break by id, so each resync picks the same tasks.
     */
    fun soonest(
        tasks: List<Task>,
        settings: ReminderSettings,
        now: Instant,
        zone: ZoneId,
        limit: Int,
    ): Map<String, Instant> =
        tasks.mapNotNull { task -> fireTime(task, settings, now, zone)?.let { task.id to it } }
            .sortedWith(compareBy({ it.second }, { it.first }))
            .take(limit)
            .toMap()

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

/**
 * The contextual notification-permission ask (spec §11.2): once, when an edit gives a task a due date
 * with a reminder, and never at launch. Reminders default to on, so waiting for the Settings switch
 * meant the ask never came (review finding 7).
 */
object ReminderPermission {
    fun shouldAsk(settings: ReminderSettings, granted: Boolean, before: Task, after: Task): Boolean {
        if (!settings.enabled || settings.permissionAsked || granted) return false
        if (after.dueDate == null || !after.notificationEnabled) return false
        return after.dueDate != before.dueDate || after.notifyBefore != before.notifyBefore
    }
}
