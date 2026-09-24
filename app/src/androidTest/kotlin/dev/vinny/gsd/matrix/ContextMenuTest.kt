package dev.vinny.gsd.matrix

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class ContextMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun task(id: String = "task-under-test-00000", deps: List<String> = emptyList()) =
        Task(id = id, title = "menu me", urgent = true, important = true,
            createdAt = t0, updatedAt = t0, dependencies = deps)

    @Test
    fun longPressOpensMenuWithActions() {
        var duplicated = false
        rule.setContent {
            GsdTheme {
                TaskRowWithMenu(
                    task = task(),
                    badges = TaskBadges(0, 0),
                    actions = TaskRowActions(onDuplicate = { duplicated = true }),
                )
            }
        }
        rule.onNodeWithText("menu me").performTouchInput { longClick() }
        listOf("Edit", "Complete", "Start timer", "Snooze", "Duplicate", "Share", "Move to", "Delete")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Duplicate").performClick()
        assertEquals(true, duplicated)
    }

    /** Review finding 1: TalkBack announces the button as "Task options", and it opened nothing. */
    @Test
    fun theOptionsButtonOpensTheSameMenu() {
        var duplicated = false
        rule.setContent {
            GsdTheme {
                TaskRowWithMenu(
                    task = task(),
                    badges = TaskBadges(0, 0),
                    actions = TaskRowActions(onDuplicate = { duplicated = true }),
                )
            }
        }
        rule.onNodeWithContentDescription("Task options").performClick()
        listOf("Edit", "Complete", "Start timer", "Snooze", "Duplicate", "Share", "Move to", "Delete")
            .forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Duplicate").performClick()
        assertEquals(true, duplicated)
    }

    /** Trash, Archive, and the tablet grid draw a bare card. A button with no menu behind it is a dead control. */
    @Test
    fun aCardWithNoMenuShowsNoOptionsButton() {
        rule.setContent {
            GsdTheme { TaskCard(task = task(), onToggleComplete = {}, onClick = {}) }
        }
        rule.onNodeWithText("menu me").assertIsDisplayed()
        rule.onNodeWithContentDescription("Task options").assertDoesNotExist()
    }

    @Test
    fun blockedBadgeShowsCount() {
        rule.setContent {
            GsdTheme {
                TaskCard(
                    task = task(deps = listOf("blocker-id-000000000")),
                    onToggleComplete = {},
                    onClick = {},
                    badges = TaskBadges(blocked = 1, blocking = 2),
                )
            }
        }
        rule.onNodeWithContentDescription("Blocked by 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Blocking 2").assertIsDisplayed()
    }

    /** Review finding 31: Snooze and Move to open submenus, and nothing in the menu said so. */
    @Test
    fun theTwoSubmenuItemsCarryACue() {
        rule.setContent {
            GsdTheme { TaskRowWithMenu(task = task(), badges = TaskBadges(0, 0), actions = TaskRowActions()) }
        }

        rule.onNodeWithContentDescription("Task options").performClick()

        rule.onAllNodesWithTag("submenu_cue", useUnmergedTree = true).assertCountEquals(2)
    }
}
