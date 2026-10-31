package dev.vinny.gsd.model

/**
 * The per-device "Escalate tasks due within" setting (spec-escalation.md §4, D-12). A null window is
 * Off. It is stored as an int where 0 means Off, so the stored form needs no separate "unset" marker.
 */
object EscalationWindow {
    const val DEFAULT: Int = 2

    /** Off, 1 day, 2 days, 3 days, 1 week, in the order the setting lists them. */
    val OPTIONS: List<Int?> = listOf(null, 1, 2, 3, 7)

    private const val OFF_STORED = 0

    fun toStored(window: Int?): Int = window ?: OFF_STORED

    /** Nothing stored, or a value the setting doesn't offer, reads as the default. */
    fun fromStored(raw: Int?): Int? = when {
        raw == null -> DEFAULT
        raw == OFF_STORED -> null
        raw in OPTIONS -> raw
        else -> DEFAULT
    }
}
