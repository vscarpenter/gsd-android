package dev.vinny.gsd.model

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Outbound share rendering (spec §8.18): plain text — title, quadrant title,
 * medium date when due, tags, then a blank line and the description. No JSON,
 * no server links.
 */
object ShareText {
    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

    fun render(task: Task, zone: ZoneId): String = buildString {
        appendLine(task.title)
        append(task.quadrant.title)
        task.dueDate?.let { append("\n").append(dateFormat.format(it.atZone(zone))) }
        if (task.tags.isNotEmpty()) {
            append("\n").append(task.tags.joinToString(" ") { "#$it" })
        }
        if (task.description.isNotEmpty()) {
            append("\n\n").append(task.description)
        }
    }
}

/** Duplicate = a fresh copy: new ids for task and subtasks, incomplete, no time entries (spec §8.1). */
object TaskDuplicator {
    fun duplicate(original: Task, now: java.time.Instant): Task = original.copy(
        id = NanoId.generate(),
        completed = false,
        completedAt = null,
        createdAt = now,
        updatedAt = now,
        subtasks = original.subtasks.map { it.copy(id = NanoId.generate(), completed = false) },
        timeEntries = emptyList(),
        parentTaskId = null,
        notificationSent = false,
        lastNotificationAt = null,
        snoozedUntil = null,
        focusDate = null, // a new task starts with no pick (spec-top-three.md §3.2)
        focusRank = null,
    )
}
