package dev.vinny.gsd.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class TaskEditorTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun draft(title: String = "t", description: String = "") =
        Task(
            title = title, description = description, urgent = false, important = false,
            createdAt = t0, updatedAt = t0,
        )

    @Test
    fun saveDisabledWhileTitleEmpty() {
        rule.setContent {
            GsdTheme { EditorContent(draft = draft(title = ""), canSave = false, onEdit = {}, onSave = {}, defaultReminder = 15) }
        }
        rule.onNodeWithText("Save").assertIsNotEnabled()
    }

    @Test
    fun quadrantPickerReflectsAndUpdatesFlags() {
        val base = draft()
        var edited: Task? = null
        rule.setContent {
            GsdTheme {
                EditorContent(draft = base, canSave = true, onEdit = { edited = it(base) }, onSave = {}, defaultReminder = 15)
            }
        }
        rule.onNodeWithText("Do First").performClick()
        assertEquals(true, edited?.urgent)
        assertEquals(true, edited?.important)
    }

    @Test
    fun linksAutoDetectFromNotes() {
        rule.setContent {
            GsdTheme {
                EditorContent(
                    draft = draft(description = "see https://example.com/spec"),
                    canSave = true,
                    onEdit = {},
                    onSave = {}, defaultReminder = 15,
                )
            }
        }
        rule.onNodeWithText("https://example.com/spec").assertIsDisplayed()
    }

    @Test
    fun tagChipCommitAddsNormalizedTag() {
        rule.setContent {
            GsdTheme {
                EditorContent(
                    draft = draft().copy(tags = listOf("work")),
                    canSave = true,
                    onEdit = {},
                    onSave = {}, defaultReminder = 15,
                )
            }
        }
        rule.onNodeWithText("#work").assertIsDisplayed()
        rule.onNodeWithText("Remove tag work").assertDoesNotExist() // cd, not text
    }
}
