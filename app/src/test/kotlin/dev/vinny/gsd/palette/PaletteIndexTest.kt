package dev.vinny.gsd.palette

import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteIndexTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(title: String) =
        Task(title = title, urgent = false, important = false, createdAt = t0, updatedAt = t0)

    /** The English strings the app resolves from resources. */
    private val actionLabel: (PaletteAction) -> String = {
        when (it) {
            PaletteAction.NEW_TASK -> "New task"
            PaletteAction.TOGGLE_SHOW_COMPLETED -> "Toggle show completed"
            PaletteAction.TOGGLE_THEME -> "Toggle theme"
            PaletteAction.SEND_FEEDBACK -> "Send feedback"
        }
    }
    private val navLabel: (PaletteNav) -> String = { it.name.lowercase().replaceFirstChar(Char::uppercase) }

    private fun query(raw: String, tasks: List<Task> = emptyList(), views: List<SmartView> = emptyList()) =
        PaletteIndex.query(raw, tasks, views, actionLabel, navLabel)

    @Test
    fun `empty query lists actions and navigation, no tasks`() {
        val results = query("", listOf(task("a")), BuiltInSmartViews.all)
        assertEquals(0, results.tasks.size)
        assertEquals(PaletteAction.entries.size, results.actions.size)
        assertEquals(PaletteNav.entries.size, results.navigation.size)
    }

    @Test
    fun `substring matches are case-insensitive across sections`() {
        val results = query("WEEK", listOf(task("weekly review")), BuiltInSmartViews.all)
        assertEquals(listOf("weekly review"), results.tasks.map { it.title })
        assertTrue(results.views.any { it.name == "This Week" })
        assertTrue(results.views.any { it.name == "This Week's Wins" })
    }

    @Test
    fun `actions and navigation filter by label`() {
        val results = query("theme")
        assertEquals(listOf(PaletteAction.TOGGLE_THEME), results.actions)
        assertEquals(0, results.navigation.size)

        val nav = query("dash")
        assertEquals(listOf(PaletteNav.DASHBOARD), nav.navigation)
    }

    @Test
    fun `substring not fuzzy`() {
        val results = query("wkly", listOf(task("weekly review")))
        assertEquals(0, results.tasks.size)
    }

    @Test
    fun `send feedback is an action and matches by substring`() {
        assertTrue(PaletteAction.SEND_FEEDBACK in query("").actions)
        assertEquals(listOf(PaletteAction.SEND_FEEDBACK), query("feedb").actions)
    }

    /** Review, Copy: the labels were English literals in Kotlin. A query matches the words the palette shows. */
    @Test
    fun `commands match the labels they are given`() {
        val french = PaletteIndex.query(
            raw = "thème",
            tasks = emptyList(),
            views = emptyList(),
            actionLabel = { if (it == PaletteAction.TOGGLE_THEME) "Changer de thème" else "" },
            navLabel = { if (it == PaletteNav.SETTINGS) "Réglages" else "" },
        )
        assertEquals(listOf(PaletteAction.TOGGLE_THEME), french.actions)
        assertEquals(0, french.navigation.size)
    }
}
