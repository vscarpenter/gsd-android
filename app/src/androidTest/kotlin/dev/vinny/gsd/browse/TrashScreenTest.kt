package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
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

    /** Review finding 48: the disc did nothing here, and TalkBack announced it as "Mark complete". */
    @Test
    fun aTrashedCardShowsNoCompletionDisc() {
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(trashedTask("old thing") to t0),
                    now = t0,
                    onRestore = {},
                    onDeleteForever = {},
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithText("old thing").assertIsDisplayed()
        rule.onNodeWithContentDescription("Mark complete").assertDoesNotExist()
        rule.onNodeWithContentDescription("Mark incomplete").assertDoesNotExist()
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
        rule.onNodeWithText("Delete \"old thing\" forever?").assertIsDisplayed() // finding 31: it names the task
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
    fun bulkRestoreFiresForEverySelectedTask() {
        val taskA = trashedTask("first thing")
        val taskB = trashedTask("second thing")
        val restored = mutableListOf<String>()
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(taskA to t0, taskB to t0),
                    now = t0,
                    onRestore = { restored += it },
                    onDeleteForever = {},
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Select tasks").performClick()
        // Selection mode hides the per-row Restore/Delete forever buttons — the
        // row checkboxes are the selection UI; check both.
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onAllNodes(isToggleable())[1].performClick()
        rule.onNodeWithText("2 selected").assertIsDisplayed()

        rule.onNodeWithText("Restore").performClick() // the bulk bar's — no per-row buttons in this mode
        assertEquals(setOf(taskA.id, taskB.id), restored.toSet())
    }

    @Test
    fun bulkDeleteForeverConfirmsAndOnlyDeletesAfterConfirm() {
        val taskA = trashedTask("first thing")
        val taskB = trashedTask("second thing")
        val deleted = mutableListOf<String>()
        rule.setContent {
            GsdTheme {
                TrashContent(
                    trashed = listOf(taskA to t0, taskB to t0),
                    now = t0,
                    onRestore = {},
                    onDeleteForever = { deleted += it },
                    onEmptyTrash = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Select tasks").performClick()
        rule.onAllNodes(isToggleable())[0].performClick()
        rule.onAllNodes(isToggleable())[1].performClick()

        rule.onNodeWithText("Delete forever").performClick() // bulk bar button — only match while dialog is closed
        assertEquals(emptyList(), deleted) // confirmation dialog gates it
        rule.onNodeWithText("Delete 2 tasks?").assertIsDisplayed()

        // Cancel deletes nothing
        rule.onNodeWithText("Cancel").performClick()
        assertEquals(emptyList(), deleted)

        // Confirming deletes every selected task
        rule.onNodeWithText("Delete forever").performClick()
        rule.onAllNodesWithText("Delete forever")[1].performClick() // [0] bulk bar, [1] dialog confirm
        assertEquals(setOf(taskA.id, taskB.id), deleted.toSet())
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
