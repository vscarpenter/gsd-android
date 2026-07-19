package dev.vinny.gsd.model

import java.time.Instant
import java.time.temporal.ChronoUnit

sealed class TaskValidationError(message: String) : Exception(message) {
    class TitleLength : TaskValidationError("Title must be 1–80 characters")
    class DescriptionLength : TaskValidationError("Description is limited to 600 characters")
    class TooManyTags : TaskValidationError("At most 20 tags")
    class TagLength(tag: String) : TaskValidationError("Tag \"$tag\" must be 1–30 characters")
    class TooManySubtasks : TaskValidationError("At most 50 subtasks")
    class SubtaskTitleLength : TaskValidationError("Subtask titles must be 1–100 characters")
    class TooManyDependencies : TaskValidationError("At most 50 dependencies")
    class EstimateRange : TaskValidationError("Estimate must be 1–10080 minutes")
    class TooManyTimeEntries : TaskValidationError("At most 1000 time entries")
    class NoteLength : TaskValidationError("Time entry notes are limited to 200 characters")
    class IdTooShort : TaskValidationError("IDs must be at least 4 characters")
}

/**
 * Applied on every write path and on import (spec §7.11). Throws typed errors —
 * never silent truncation. The only coercions the spec allows: tag normalization,
 * estimate 0 → unset, snooze clamped to 365 days.
 */
object TaskValidator {
    fun normalizeTags(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

    fun validate(task: Task, now: Instant): Task {
        if (task.id.length < 4) throw TaskValidationError.IdTooShort()
        if (task.title.isEmpty() || task.title.length > 80) throw TaskValidationError.TitleLength()
        if (task.description.length > 600) throw TaskValidationError.DescriptionLength()
        val tags = normalizeTags(task.tags)
        if (tags.size > 20) throw TaskValidationError.TooManyTags()
        tags.firstOrNull { it.length > 30 }?.let { throw TaskValidationError.TagLength(it) }
        if (task.subtasks.size > 50) throw TaskValidationError.TooManySubtasks()
        if (task.subtasks.any { it.title.isEmpty() || it.title.length > 100 }) {
            throw TaskValidationError.SubtaskTitleLength()
        }
        if (task.dependencies.size > 50) throw TaskValidationError.TooManyDependencies()
        val estimate = task.estimatedMinutes?.takeIf { it != 0 }
        if (estimate != null && estimate !in 1..10_080) throw TaskValidationError.EstimateRange()
        if (task.timeEntries.size > 1000) throw TaskValidationError.TooManyTimeEntries()
        if (task.timeEntries.any { (it.notes?.length ?: 0) > 200 }) throw TaskValidationError.NoteLength()
        val snoozeCap = now.plus(365, ChronoUnit.DAYS)
        val snooze = task.snoozedUntil?.let { if (it.isAfter(snoozeCap)) snoozeCap else it }
        return task.copy(tags = tags, estimatedMinutes = estimate, snoozedUntil = snooze)
    }
}
