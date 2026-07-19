package dev.vinny.gsd.model

import java.time.Instant

enum class Recurrence(val wire: String) {
    NONE("none"), DAILY("daily"), WEEKLY("weekly"), MONTHLY("monthly");

    companion object {
        /** Unknown wire values degrade to NONE rather than failing (spec §7.10). */
        fun fromWire(wire: String?): Recurrence = entries.firstOrNull { it.wire == wire } ?: NONE
    }
}

data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean = false,
)

data class TimeEntry(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val notes: String? = null,
)

/**
 * The core entity (spec §7.2). createdAt/updatedAt carry no defaults on purpose:
 * the store stamps them from its injected clock, keeping time out of the domain.
 */
data class Task(
    val id: String = NanoId.generate(),
    val title: String,
    val description: String = "",
    val urgent: Boolean,
    val important: Boolean,
    val completed: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val dueDate: Instant? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val parentTaskId: String? = null,
    val notifyBefore: Int? = null,
    val notificationEnabled: Boolean = true,
    val notificationSent: Boolean = false,
    val lastNotificationAt: Instant? = null,
    val snoozedUntil: Instant? = null,
    val estimatedMinutes: Int? = null,
    val timeEntries: List<TimeEntry> = emptyList(),
) {
    val quadrant: Quadrant get() = Quadrant.from(urgent, important)

    /** Total tracked whole minutes over completed entries (spec §8.9). */
    val timeSpent: Int
        get() = timeEntries.sumOf { entry ->
            entry.endedAt
                ?.let { ((it.toEpochMilli() - entry.startedAt.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0) }
                ?: 0
        }
}
