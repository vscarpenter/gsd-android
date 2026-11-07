package dev.vinny.gsd.model

/** What the editor's Save means for a task that already exists (spec §8.3). */
object EditorRules {
    /**
     * The row Save writes (review finding 44). The editor holds three versions of the task: the row it
     * opened, the row the user edited, and the row now in the store. For each field the editor owns,
     * the user's value wins only where the user changed it. Everything else is the stored row, so a
     * completion, a timer, or a snooze that landed while the sheet was open survives.
     *
     * Editing dueDate or notifyBefore resets reminder state, so the user is notified again (spec §8.3).
     * The offset compares effective values against [defaultReminder] (D-10): resolving a legacy null to
     * the number it already fires at isn't a move, so opening and saving an old task re-fires nothing.
     */
    fun merge(current: Task, original: Task, edited: Task, defaultReminder: Int): Task {
        fun <T> pick(field: (Task) -> T): T =
            if (field(edited) != field(original)) field(edited) else field(current)
        val merged = current.copy(
            title = pick { it.title },
            description = pick { it.description },
            urgent = pick { it.urgent },
            important = pick { it.important },
            tags = pick { it.tags },
            dueDate = pick { it.dueDate },
            recurrence = pick { it.recurrence },
            subtasks = pick { it.subtasks },
            dependencies = pick { it.dependencies },
            notifyBefore = pick { it.notifyBefore },
            notificationEnabled = pick { it.notificationEnabled },
            estimatedMinutes = pick { it.estimatedMinutes },
            snoozedUntil = pick { it.snoozedUntil },
        )
        return if (movedTheReminder(original, edited, defaultReminder)) merged.withReminderReset() else merged
    }

    private fun movedTheReminder(original: Task, edited: Task, defaultReminder: Int): Boolean =
        edited.dueDate != original.dueDate ||
            ReminderOffset.effective(edited, defaultReminder) != ReminderOffset.effective(original, defaultReminder)

    private fun Task.withReminderReset(): Task =
        copy(notificationSent = false, lastNotificationAt = null, snoozedUntil = null)
}
