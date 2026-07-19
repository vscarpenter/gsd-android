package dev.vinny.gsd.model

import java.time.Instant
import java.time.ZoneOffset

/**
 * Recurring-task spawning (spec §8.5). Completing a recurring task creates a
 * fresh instance; the completed original stays behind as history. Date math is
 * done in UTC — wire timestamps are instants and "+1 month" clamps to month end.
 */
object RecurrenceEngine {
    fun nextDueDate(prior: Instant, recurrence: Recurrence): Instant? {
        val zoned = prior.atZone(ZoneOffset.UTC)
        return when (recurrence) {
            Recurrence.NONE -> null
            Recurrence.DAILY -> zoned.plusDays(1).toInstant()
            Recurrence.WEEKLY -> zoned.plusDays(7).toInstant()
            Recurrence.MONTHLY -> zoned.plusMonths(1).toInstant() // java.time clamps Jan 31 → Feb 28/29
        }
    }

    fun spawnNext(completed: Task, now: Instant): Task? {
        if (completed.recurrence == Recurrence.NONE) return null
        return completed.copy(
            id = NanoId.generate(),
            completed = false,
            completedAt = null,
            createdAt = now,
            updatedAt = now,
            dueDate = completed.dueDate?.let { nextDueDate(it, completed.recurrence) },
            parentTaskId = completed.parentTaskId ?: completed.id,
            subtasks = completed.subtasks.map { it.copy(completed = false) },
            timeEntries = emptyList(),
            notificationSent = false,
            lastNotificationAt = null,
            snoozedUntil = null,
        )
    }
}
