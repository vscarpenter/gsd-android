package dev.vinny.gsd.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Recurrence(val wire: String) {
    @SerialName("none") NONE("none"),
    @SerialName("daily") DAILY("daily"),
    @SerialName("weekly") WEEKLY("weekly"),
    @SerialName("monthly") MONTHLY("monthly");

    companion object {
        /** Unknown wire values degrade to NONE rather than failing (spec §7.10). */
        fun fromWire(wire: String?): Recurrence = entries.firstOrNull { it.wire == wire } ?: NONE
    }
}

@Serializable
data class Subtask(
    val id: String,
    val title: String,
    val completed: Boolean = false,
)

@Serializable
data class TimeEntry(
    val id: String,
    @Serializable(with = InstantIso8601Serializer::class) val startedAt: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val endedAt: Instant? = null,
    val notes: String? = null,
)

/**
 * The core entity (spec §7.2). createdAt/updatedAt carry no defaults on purpose:
 * the store stamps them from its injected clock, keeping time out of the domain.
 */
@Serializable
data class Task(
    val id: String = NanoId.generate(),
    val title: String,
    val description: String = "",
    val urgent: Boolean,
    val important: Boolean,
    val completed: Boolean = false,
    @Serializable(with = InstantIso8601Serializer::class) val completedAt: Instant? = null,
    @Serializable(with = InstantIso8601Serializer::class) val createdAt: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val updatedAt: Instant,
    @Serializable(with = InstantIso8601Serializer::class) val dueDate: Instant? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val tags: List<String> = emptyList(),
    val subtasks: List<Subtask> = emptyList(),
    val dependencies: List<String> = emptyList(),
    val parentTaskId: String? = null,
    val notifyBefore: Int? = null,
    val notificationEnabled: Boolean = true,
    val notificationSent: Boolean = false,
    @Serializable(with = InstantIso8601Serializer::class) val lastNotificationAt: Instant? = null,
    @Serializable(with = InstantIso8601Serializer::class) val snoozedUntil: Instant? = null,
    val estimatedMinutes: Int? = null,
    val timeEntries: List<TimeEntry> = emptyList(),
    /** Non-null keeps the task outside the matrix (web ADR 0018). Synced as `saved_at`. */
    @Serializable(with = InstantIso8601Serializer::class) val savedAt: Instant? = null,
) {
    val quadrant: Quadrant get() = Quadrant.from(urgent, important)

    val isSaved: Boolean get() = savedAt != null

    /** Total tracked whole minutes over completed entries (spec §8.9). */
    val timeSpent: Int
        get() = timeEntries.sumOf { entry ->
            entry.endedAt
                ?.let { ((it.toEpochMilli() - entry.startedAt.toEpochMilli()) / 60_000L).toInt().coerceAtLeast(0) }
                ?: 0
        }
}
