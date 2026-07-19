package dev.vinny.gsd.model

/**
 * Eisenhower quadrant (spec §7.8). Always derived from the urgent/important
 * flags — never persisted independently. Declaration order is display order.
 */
enum class Quadrant(val id: String, val title: String) {
    DO_FIRST("urgent-important", "Do First"),
    SCHEDULE("not-urgent-important", "Schedule"),
    DELEGATE("urgent-not-important", "Delegate"),
    ELIMINATE("not-urgent-not-important", "Eliminate");

    companion object {
        fun from(urgent: Boolean, important: Boolean): Quadrant = when {
            urgent && important -> DO_FIRST
            !urgent && important -> SCHEDULE
            urgent -> DELEGATE
            else -> ELIMINATE
        }

        fun fromId(id: String): Quadrant? = entries.firstOrNull { it.id == id }
    }
}
