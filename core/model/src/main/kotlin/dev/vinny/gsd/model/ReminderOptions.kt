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
