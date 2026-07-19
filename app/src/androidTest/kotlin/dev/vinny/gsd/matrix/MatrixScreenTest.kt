package dev.vinny.gsd.matrix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatrixScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(title: String, urgent: Boolean, important: Boolean, completed: Boolean = false) =
        Task(
            title = title, urgent = urgent, important = important, completed = completed,
            createdAt = t0, updatedAt = t0,
        )

    @Test
    fun tasksGroupIntoQuadrantSectionsInOrder() {
        val sections = buildSections(
            listOf(
                task("fire", urgent = true, important = true),
                task("plan", urgent = false, important = true),
            ),
        )
        rule.setContent { GsdTheme { MatrixList(sections, onToggleComplete = {}, onOpen = {}) } }
        rule.onNodeWithText("Do First").assertIsDisplayed()
        rule.onNodeWithText("fire").assertIsDisplayed()
        rule.onNodeWithText("Schedule").assertIsDisplayed()
        rule.onNodeWithText("No fires right now.").assertDoesNotExist()
    }

    @Test
    fun emptyQuadrantShowsTailoredPrompt() {
        rule.setContent { GsdTheme { MatrixList(buildSections(emptyList()), onToggleComplete = {}, onOpen = {}) } }
        rule.onNodeWithText("No fires right now.").assertIsDisplayed()
        rule.onNodeWithText("Nothing to drop.").assertIsDisplayed()
    }

    @Test
    fun discTapTogglesWithoutOpeningEditor() {
        val target = task("t", urgent = true, important = true)
        var toggled: String? = null
        var opened: String? = null
        rule.setContent {
            GsdTheme {
                MatrixList(
                    buildSections(listOf(target)),
                    onToggleComplete = { toggled = it.id },
                    onOpen = { opened = it },
                )
            }
        }
        rule.onNodeWithContentDescription("Mark complete").performClick()
        assertEquals(target.id, toggled)
        assertNull(opened)
    }

    @Test
    fun completedSortBelowIncompleteAndCountIsActiveOnly() {
        val sections = buildSections(
            listOf(
                task("done", urgent = true, important = true, completed = true),
                task("open", urgent = true, important = true),
            ),
        )
        val q1 = sections.first()
        assertEquals(listOf("open", "done"), q1.tasks.map { it.title })
        assertEquals(1, q1.activeCount)
    }
}
