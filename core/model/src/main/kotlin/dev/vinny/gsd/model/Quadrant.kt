package dev.vinny.gsd.model

/**
 * Eisenhower quadrant (spec §7.8). Always derived from the urgent/important
 * flags — never persisted independently. Declaration order is display order.
 */
enum class Quadrant(val id: String, val title: String, val urgent: Boolean, val important: Boolean) {
    DO_FIRST("urgent-important", "Do First", urgent = true, important = true),
    SCHEDULE("not-urgent-important", "Schedule", urgent = false, important = true),
    DELEGATE("urgent-not-important", "Delegate", urgent = true, important = false),
    ELIMINATE("not-urgent-not-important", "Eliminate", urgent = false, important = false);

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
