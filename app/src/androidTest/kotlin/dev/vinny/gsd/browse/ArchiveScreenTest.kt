package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArchiveScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun archivedTask(title: String) =
        Task(title = title, urgent = false, important = false, completed = true,
            completedAt = t0, createdAt = t0, updatedAt = t0)

    @Test
    fun restoreFiresAndPermanentDeleteConfirmsFirst() {
        val task = archivedTask("old thing")
        var restored: String? = null
        var deleted: String? = null
        rule.setContent {
            GsdTheme {
                ArchiveContent(
                    archived = listOf(task),
                    autoArchiveEnabled = false,
                    archiveAfterDays = 30,
                    onRestore = { restored = it },
                    onDeletePermanently = { deleted = it },
                    onSetAutoArchive = { _, _ -> },
                    onArchiveNow = {},
                )
            }
        }
        rule.onNodeWithText("old thing").assertIsDisplayed()
        rule.onNodeWithText("Restore").performClick()
        assertEquals(task.id, restored)

        rule.onNodeWithText("Delete permanently").performClick()
        assertNull(deleted) // confirmation dialog gates it
        rule.onNodeWithText("This can't be undone.").assertIsDisplayed()
        rule.onAllNodesWithText("Delete permanently")[1].performClick()
        assertEquals(task.id, deleted)
    }

    @Test
    fun emptyArchiveShowsQuietState() {
        rule.setContent {
            GsdTheme {
                ArchiveContent(
                    archived = emptyList(),
                    autoArchiveEnabled = false,
                    archiveAfterDays = 30,
                    onRestore = {},
                    onDeletePermanently = {},
                    onSetAutoArchive = { _, _ -> },
                    onArchiveNow = {},
                )
            }
        }
        rule.onNodeWithText("Nothing archived yet").assertIsDisplayed()
    }
}
