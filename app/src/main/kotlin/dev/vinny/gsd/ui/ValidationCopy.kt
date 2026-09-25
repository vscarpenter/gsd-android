package dev.vinny.gsd.ui

import android.content.res.Resources
import dev.vinny.gsd.R
import dev.vinny.gsd.model.TaskValidationError

/**
 * What a validation failure tells the person, from resources. The model's own message is for logs
 * and tests (review, Copy).
 */
fun Resources.validationMessage(error: TaskValidationError): String = when (error) {
    is TaskValidationError.TitleLength -> getString(R.string.invalid_title)
    is TaskValidationError.DescriptionLength -> getString(R.string.invalid_notes)
    is TaskValidationError.TooManyTags -> getString(R.string.tags_at_limit)
    is TaskValidationError.TagLength -> getString(R.string.invalid_tag, error.tag)
    is TaskValidationError.TooManySubtasks -> getString(R.string.subtasks_at_limit)
    is TaskValidationError.SubtaskTitleLength -> getString(R.string.invalid_subtask)
    is TaskValidationError.TooManyDependencies -> getString(R.string.invalid_dependencies)
    is TaskValidationError.EstimateRange -> getString(R.string.invalid_estimate)
    is TaskValidationError.TooManyTimeEntries -> getString(R.string.invalid_time_entries)
    is TaskValidationError.NoteLength -> getString(R.string.invalid_time_note)
    // Only a malformed import or sync record gets here; the app makes its own ids long enough
    is TaskValidationError.IdTooShort -> getString(R.string.invalid_task)
}
