package dev.vinny.gsd.model

import java.time.Instant
import java.time.temporal.ChronoUnit

sealed class TaskValidationError(message: String) : Exception(message) {
    class TitleLength : TaskValidationError("Title must be 1–80 characters")
    class DescriptionLength : TaskValidationError("Description is limited to 600 characters")
    class TooManyTags : TaskValidationError("At most 20 tags")
    class TagLength(val tag: String) : TaskValidationError("Tag \"$tag\" must be 1–30 characters")
    class TooManySubtasks : TaskValidationError("At most 50 subtasks")
    class SubtaskTitleLength : TaskValidationError("Subtask titles must be 1–100 characters")
    class TooManyDependencies : TaskValidationError("At most 50 dependencies")
    class EstimateRange : TaskValidationError("Estimate must be 1–10080 minutes")
    class TooManyTimeEntries : TaskValidationError("At most 1000 time entries")
    class NoteLength : TaskValidationError("Time entry notes are limited to 200 characters")
    class IdTooShort : TaskValidationError("IDs must be at least 4 characters")
}

/**
 * Two levels (spec §7.11). [validate] is for what the user types: the editor, capture, share, and
 * import. It throws typed errors and never truncates. [normalize] is for a state change to a task
 * that is already stored. It applies only the coercions the spec allows, and it never throws.
 */
object TaskValidator {
    fun normalizeTags(raw: List<String>): List<String> =
        raw.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Tag normalization, estimate 0 to unset, and the 365-day snooze cap. A synced task can break a
     * limit here, because iOS counts characters and Android counts UTF-16 units. Completing or
     * snoozing it must still work, so nothing in this function rejects.
     */
    fun normalize(task: Task, now: Instant): Task {
        val snoozeCap = now.plus(365, ChronoUnit.DAYS)
        return task.copy(
            tags = normalizeTags(task.tags),
            estimatedMinutes = task.estimatedMinutes?.takeIf { it != 0 },
            snoozedUntil = task.snoozedUntil?.let { if (it.isAfter(snoozeCap)) snoozeCap else it },
        )
    }

    fun validate(task: Task, now: Instant): Task {
        val normalized = normalize(task, now)
        if (normalized.id.length < 4) throw TaskValidationError.IdTooShort()
        if (normalized.title.isEmpty() || normalized.title.length > 80) throw TaskValidationError.TitleLength()
        if (normalized.description.length > 600) throw TaskValidationError.DescriptionLength()
        if (normalized.tags.size > 20) throw TaskValidationError.TooManyTags()
        normalized.tags.firstOrNull { it.length > 30 }?.let { throw TaskValidationError.TagLength(it) }
        if (normalized.subtasks.size > 50) throw TaskValidationError.TooManySubtasks()
        if (normalized.subtasks.any { it.title.isEmpty() || it.title.length > 100 }) {
            throw TaskValidationError.SubtaskTitleLength()
        }
        if (normalized.dependencies.size > 50) throw TaskValidationError.TooManyDependencies()
        val estimate = normalized.estimatedMinutes
        if (estimate != null && estimate !in 1..10_080) throw TaskValidationError.EstimateRange()
        if (normalized.timeEntries.size > 1000) throw TaskValidationError.TooManyTimeEntries()
        if (normalized.timeEntries.any { (it.notes?.length ?: 0) > 200 }) throw TaskValidationError.NoteLength()
        return normalized
    }
}
