package dev.vinny.gsd.matrix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 13: select mode showed no selected state on any card, and the completion disc
 * still said "Mark complete" while a tap on it toggled the selection.
 */
class SelectedCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private val task = Task(title = "pick me", urgent = true, important = true, createdAt = t0, updatedAt = t0)

    @Test
    fun aCardInSelectModeIsACheckboxWithNoCompletionDisc() {
        var selected by mutableStateOf(false)
        rule.setContent {
            GsdTheme {
                TaskCard(task = task, onToggleComplete = {}, onClick = { selected = !selected }, selected = selected)
            }
        }

        rule.onNodeWithContentDescription("Mark complete").assertDoesNotExist()
        rule.onNodeWithText(task.title).assertIsOff()
        rule.onNodeWithText(task.title).performClick()
        rule.onNodeWithText(task.title).assertIsOn()
    }

    @Test
    fun aCardOutsideSelectModeKeepsItsDisc() {
        rule.setContent { GsdTheme { TaskCard(task = task, onToggleComplete = {}, onClick = {}) } }

        rule.onNodeWithContentDescription("Mark complete").assertExists()
    }
}
