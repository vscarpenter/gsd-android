package dev.vinny.gsd.matrix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class MatrixGridTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun gridRendersFourCellsWithTasksInPlace() {
        val sections = buildSections(
            listOf(
                Task(title = "urgent thing", urgent = true, important = true, createdAt = t0, updatedAt = t0),
                Task(title = "someday", urgent = false, important = false, createdAt = t0, updatedAt = t0),
            ),
        )
        rule.setContent {
            GsdTheme {
                MatrixGrid(sections, onToggleComplete = {}, onOpen = {}, onDropToQuadrant = { _, _ -> })
            }
        }
        rule.onNodeWithText("Do First").assertIsDisplayed()
        rule.onNodeWithText("Schedule").assertIsDisplayed()
        rule.onNodeWithText("Delegate").assertIsDisplayed()
        rule.onNodeWithText("Eliminate").assertIsDisplayed()
        rule.onNodeWithText("urgent thing").assertIsDisplayed()
        rule.onNodeWithText("someday").assertIsDisplayed()
    }
}
