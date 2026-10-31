package dev.vinny.gsd.model

import java.time.Instant

/**
 * Saved placement (web ADR 0018, iOS ADR 0001). A Saved task keeps its identity and context
 * outside the matrix. Saving never completes it; returning never spawns a recurrence.
 */
object SavedPlacement {
    class CannotSave(message: String) : Exception(message)

    fun canSave(task: Task): Boolean = !task.completed && TimeTracking.runningEntry(task) == null

    fun save(task: Task, now: Instant): Task {
        if (task.completed) throw CannotSave("A completed task can't move to Saved")
        if (TimeTracking.runningEntry(task) != null) throw CannotSave("Stop the timer before saving this task")
        return task.copy(savedAt = task.savedAt ?: now)
    }

    /** The Saved list, newest first. [query] matches the title, the notes, or a tag, ignoring case. */
    fun library(tasks: List<Task>, query: String = ""): List<Task> {
        val q = query.trim().lowercase()
        return tasks
            .filter { it.isSaved }
            .filter { task ->
                q.isEmpty() || q in task.title.lowercase() || q in task.description.lowercase() ||
                    task.tags.any { q in it.lowercase() }
            }
            .sortedWith(compareByDescending<Task> { it.savedAt }.thenBy { it.id })
    }

    /** Back to active work with the quadrant and date the user reviewed. */
    fun returnToMatrix(task: Task, urgent: Boolean, important: Boolean, dueDate: Instant?): Task =
        resetReminderState(task.copy(savedAt = null, urgent = urgent, important = important, dueDate = dueDate))

    /** A return made on another device resets this device's reminder state, as a local return does. */
    fun afterRemoteChange(local: Task?, incoming: Task): Task =
        if (local?.isSaved == true && !incoming.isSaved) resetReminderState(incoming) else incoming

    private fun resetReminderState(task: Task): Task =
        task.copy(notificationSent = false, lastNotificationAt = null, snoozedUntil = null)
}
