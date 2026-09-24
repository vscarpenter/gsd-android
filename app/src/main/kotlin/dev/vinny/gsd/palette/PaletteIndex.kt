package dev.vinny.gsd.palette

import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task

/** Palette commands. Their words come from resources, through the labels [PaletteIndex.query] takes. */
enum class PaletteAction { NEW_TASK, TOGGLE_SHOW_COMPLETED, TOGGLE_THEME, SEND_FEEDBACK }

enum class PaletteNav { MATRIX, DASHBOARD, ARCHIVE, SETTINGS }

data class PaletteResults(
    val tasks: List<Task>,
    val views: List<SmartView>,
    val actions: List<PaletteAction>,
    val navigation: List<PaletteNav>,
)

/**
 * Palette matching (spec §8.14): plain case-insensitive substring, never fuzzy.
 * Empty query surfaces commands, not the whole task list. A command matches the words the palette
 * shows for it, [actionLabel] and [navLabel], so the query and the row always agree.
 */
object PaletteIndex {
    fun query(
        raw: String,
        tasks: List<Task>,
        views: List<SmartView>,
        actionLabel: (PaletteAction) -> String,
        navLabel: (PaletteNav) -> String,
    ): PaletteResults {
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
            actions = PaletteAction.entries.filter { q in actionLabel(it).lowercase() },
            navigation = PaletteNav.entries.filter { q in navLabel(it).lowercase() },
        )
    }
}
