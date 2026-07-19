package dev.vinny.gsd.palette

import dev.vinny.gsd.model.BuiltInSmartViews
import dev.vinny.gsd.model.Task
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PaletteIndexTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(title: String) =
        Task(title = title, urgent = false, important = false, createdAt = t0, updatedAt = t0)

    @Test
    fun `empty query lists actions and navigation, no tasks`() {
        val results = PaletteIndex.query("", listOf(task("a")), BuiltInSmartViews.all)
        assertEquals(0, results.tasks.size)
        assertEquals(PaletteAction.entries.size, results.actions.size)
        assertEquals(PaletteNav.entries.size, results.navigation.size)
    }

    @Test
    fun `substring matches are case-insensitive across sections`() {
        val results = PaletteIndex.query("WEEK", listOf(task("weekly review")), BuiltInSmartViews.all)
        assertEquals(listOf("weekly review"), results.tasks.map { it.title })
        assertTrue(results.views.any { it.name == "This Week" })
        assertTrue(results.views.any { it.name == "This Week's Wins" })
    }

    @Test
    fun `actions and navigation filter by label`() {
        val results = PaletteIndex.query("theme", emptyList(), emptyList())
        assertEquals(listOf(PaletteAction.TOGGLE_THEME), results.actions)
        assertEquals(0, results.navigation.size)

        val nav = PaletteIndex.query("dash", emptyList(), emptyList())
        assertEquals(listOf(PaletteNav.DASHBOARD), nav.navigation)
    }

    @Test
    fun `substring not fuzzy`() {
        val results = PaletteIndex.query("wkly", listOf(task("weekly review")), emptyList())
        assertEquals(0, results.tasks.size)
    }
}
