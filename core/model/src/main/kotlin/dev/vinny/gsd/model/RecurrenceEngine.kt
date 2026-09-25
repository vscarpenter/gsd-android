package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneId

/**
 * Recurring-task spawning (spec §8.5). Completing a recurring task creates a
 * fresh instance; the completed original stays behind as history. Date math uses
 * the device's calendar in [zone], as iOS and the web do, so a task due at local
 * midnight stays at local midnight across a daylight-saving change. "+1 month"
 * clamps to month end.
 */
object RecurrenceEngine {
    fun nextDueDate(prior: Instant, recurrence: Recurrence, zone: ZoneId): Instant? {
        val zoned = prior.atZone(zone)
        return when (recurrence) {
            Recurrence.NONE -> null
            Recurrence.DAILY -> zoned.plusDays(1).toInstant()
            Recurrence.WEEKLY -> zoned.plusDays(7).toInstant()
            Recurrence.MONTHLY -> zoned.plusMonths(1).toInstant() // java.time clamps Jan 31 → Feb 28/29
        }
    }

    fun spawnNext(completed: Task, now: Instant, zone: ZoneId): Task? {
        if (completed.recurrence == Recurrence.NONE) return null
        return completed.copy(
            id = NanoId.generate(),
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            dueDate = completed.dueDate?.let { nextDueDate(it, completed.recurrence, zone) },
            parentTaskId = completed.parentTaskId ?: completed.id,
            subtasks = completed.subtasks.map { it.copy(completed = false) },
            timeEntries = emptyList(),
            notificationSent = false,
            lastNotificationAt = null,
            snoozedUntil = null,
        )
    }
}
