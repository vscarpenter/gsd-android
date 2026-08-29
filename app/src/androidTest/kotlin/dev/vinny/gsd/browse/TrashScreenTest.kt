package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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

class TrashScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun trashedTask(title: String) =
        Task(title = title, urgent = false, important = false, createdAt = t0, updatedAt = t0)

    @Test
    fun rowShowsDaysLeftAndRestoreFires() {
        val task = trashedTask("old thing")
        val deletedAt = t0 // deleted "now" — 30 days of retention remain
        var restored: String? = null
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(task to deletedAt),
                    now = t0,
                    onRestore = { restored = it },
                    onDeleteForever = {},
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithText("old thing").assertIsDisplayed()
        rule.onNodeWithText("30 days left").assertIsDisplayed()
        rule.onNodeWithText("Restore").performClick()
        assertEquals(task.id, restored)
    }

    @Test
    fun lastDayOfRetentionIsCalledOut() {
        val task = trashedTask("about to go")
        val deletedAt = t0
        val now = t0.plusSeconds(30L * 24 * 3600) // exactly 30 days later — the final day
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(task to deletedAt),
                    now = now,
                    onRestore = {},
                    onDeleteForever = {},
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithText("Last day").assertIsDisplayed()
    }

    @Test
    fun deleteForeverConfirmsFirst() {
        val task = trashedTask("old thing")
        var deleted: String? = null
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(task to t0),
                    now = t0,
                    onRestore = {},
                    onDeleteForever = { deleted = it },
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithText("Delete forever").performClick()
        assertNull(deleted) // confirmation dialog gates it
        rule.onNodeWithText("This can't be undone.").assertIsDisplayed()
        rule.onAllNodesWithText("Delete forever")[1].performClick()
        assertEquals(task.id, deleted)
    }

    @Test
    fun emptyTrashConfirmsThenClears() {
        val task = trashedTask("old thing")
        var emptied = false
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(task to t0),
                    now = t0,
                    onRestore = {},
                    onDeleteForever = {},
                    onEmptyTrash = { emptied = true },
                )
            }
        }
        rule.onNodeWithContentDescription("Trash options").performClick()
        rule.onNodeWithText("Empty trash").performClick()
        assertEquals(false, emptied) // confirmation dialog gates it
        rule.onNodeWithText("Empty trash?").assertIsDisplayed()
        // The toolbar menu item unmounted with the dropdown; only the dialog's
        // confirm button matches "Empty trash" now.
        rule.onNodeWithText("Empty trash").performClick()
        assertEquals(true, emptied)
    }

    @Test
    fun emptyTrashShowsQuietState() {
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = emptyList(),
                    now = t0,
                    onRestore = {},
                    onDeleteForever = {},
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithText("Trash is empty").assertIsDisplayed()
    }
}
