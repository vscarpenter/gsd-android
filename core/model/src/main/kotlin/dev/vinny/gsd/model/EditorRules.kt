package dev.vinny.gsd.model

/** Editing dueDate or notifyBefore resets reminder state so the user is re-notified (spec §8.3). */
object EditorRules {
    fun applyReminderReset(original: Task, edited: Task): Task =
        if (edited.dueDate != original.dueDate || edited.notifyBefore != original.notifyBefore) {
            edited.copy(notificationSent = false, lastNotificationAt = null, snoozedUntil = null)
        } else {
            edited
        }
}
