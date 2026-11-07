package dev.vinny.gsd.model

/**
 * The editor's reminder rows (S§5). Android keeps its superset of presets; an off-list
 * stored `notifyBefore` (anything synced from another client) is offered as its own row
 * in sorted position so opening + saving never silently rewrites it.
 */
object ReminderOptions {
    val presets: List<Int> = listOf(0, 5, 15, 30, 60, 120, 1440)

    fun including(stored: Int?): List<Int> =
        if (stored == null || stored in presets) presets else (presets + stored).sorted()
}

/** Pure label shape for a reminder offset — iOS's formula, exact and in order (S§5). */
sealed interface ReminderLabel {
    data object AtTime : ReminderLabel
    data class Minutes(val count: Int) : ReminderLabel
    data class Hours(val count: Int) : ReminderLabel
    data class Days(val count: Int) : ReminderLabel

    companion object {
        fun of(minutes: Int): ReminderLabel = when {
            minutes == 0 -> AtTime
            minutes == 1 -> Minutes(1)
            minutes < 60 || minutes % 60 != 0 -> Minutes(minutes)
            minutes < 1440 -> Hours(minutes / 60)
            minutes % 1440 != 0 -> Hours(minutes / 60)
            else -> Days(minutes / 1440)
        }
    }
}

/**
 * D-10: `notify_before` is always a number (web `tasks/spec-notify-before.md`). A task this build writes
 * stores one, and `0` means at the due time. A row stored before this build may still hold null, which
 * fires this device's default until the row is next written. That read fallback stays for good, because
 * such rows can outlive any one release.
 */
object ReminderOffset {
    fun effective(task: Task, defaultReminder: Int): Int = task.notifyBefore ?: defaultReminder

    fun resolve(task: Task, defaultReminder: Int): Task =
        if (task.notifyBefore != null) task else task.copy(notifyBefore = defaultReminder)
}
