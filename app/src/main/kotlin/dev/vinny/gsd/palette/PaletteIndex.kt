package dev.vinny.gsd.palette

import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task

enum class PaletteAction(val label: String) {
    NEW_TASK("New task"),
    TOGGLE_SHOW_COMPLETED("Toggle show completed"),
    TOGGLE_THEME("Toggle theme"),
    SEND_FEEDBACK("Send feedback"),
}

enum class PaletteNav(val label: String) {
    MATRIX("Matrix"),
    DASHBOARD("Dashboard"),
    ARCHIVE("Archive"),
    SETTINGS("Settings"),
}

data class PaletteResults(
    val tasks: List<Task>,
    val views: List<SmartView>,
    val actions: List<PaletteAction>,
    val navigation: List<PaletteNav>,
)

/**
 * Palette matching (spec §8.14): plain case-insensitive substring, never fuzzy.
 * Empty query surfaces commands, not the whole task list.
 */
object PaletteIndex {
    fun query(raw: String, tasks: List<Task>, views: List<SmartView>): PaletteResults {
        val q = raw.trim().lowercase()
        if (q.isEmpty()) {
            return PaletteResults(
                tasks = emptyList(),
                views = views,
                actions = PaletteAction.entries.toList(),
                navigation = PaletteNav.entries.toList(),
            )
        }
        return PaletteResults(
            tasks = tasks.filter { q in it.title.lowercase() }.take(8),
            views = views.filter { q in it.name.lowercase() },
            actions = PaletteAction.entries.filter { q in it.label.lowercase() },
            navigation = PaletteNav.entries.filter { q in it.label.lowercase() },
        )
    }
}
