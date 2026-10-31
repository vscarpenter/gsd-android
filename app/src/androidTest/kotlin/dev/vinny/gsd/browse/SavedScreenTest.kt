package dev.vinny.gsd.browse

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Saved screen (spec 2026-10-29 §3.6). */
class SavedScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.of("America/Chicago")
    private val today = LocalDate.of(2026, 10, 29)
    private val t0 = Instant.parse("2026-10-26T12:00:00.000Z")

    private fun task(title: String, savedAt: Instant? = t0, description: String = "", dueDate: Instant? = null) =
        Task(id = "id-$title", title = title, description = description, urgent = false, important = true,
            createdAt = t0, updatedAt = t0, savedAt = savedAt, dueDate = dueDate)

    private fun show(
        tasks: List<Task>,
        onEditNotes: (String, String) -> Unit = { _, _ -> },
        onReturn: (String, Quadrant, Instant?) -> Unit = { _, _, _ -> },
        onDelete: (String) -> Unit = {},
    ) = rule.setContent {
        GsdTheme {
            SavedContent(tasks, onEditNotes, onReturn, onDelete, today = today, zone = zone)
        }
    }

    @Test
    fun listsSavedTasksWithTheirNotesAndLeavesActiveOnesOut() {
        show(listOf(task("Read later", description = "https://example.com"), task("Active", savedAt = null)))
        rule.onNodeWithText("Read later").assertIsDisplayed()
        rule.onNodeWithText("https://example.com").assertIsDisplayed()
        rule.onNodeWithText("Active").assertDoesNotExist()
    }

    @Test
    fun anEmptyLibrarySaysHowToFillIt() {
        show(listOf(task("Active", savedAt = null)))
        rule.onNodeWithText("Nothing saved yet").assertIsDisplayed()
    }

    @Test
    fun searchNarrowsByNotes() {
        show(listOf(task("One", description = "kitchen tiles"), task("Two")))
        rule.onNodeWithTag("saved_search").performTextInput("kitchen")
        rule.onNodeWithText("One").assertIsDisplayed()
        rule.onNodeWithText("Two").assertDoesNotExist()
    }

    @Test
    fun editNotesSavesWhatWasTyped() {
        var saved: Pair<String, String>? = null
        show(listOf(task("One", description = "old")), onEditNotes = { id, notes -> saved = id to notes })
        rule.onNodeWithText("Edit notes").performClick()
        rule.onNodeWithTag("saved_notes_field").performTextReplacement("new note")
        rule.onNodeWithTag("saved_notes_save").performClick()
        assertEquals("id-One" to "new note", saved)
    }

    @Test
    fun returnUsesTheReviewedQuadrantAndKeepsTheDateByDefault() {
        val due = Instant.parse("2026-11-03T15:00:00.000Z")
        var returned: Triple<String, Quadrant, Instant?>? = null
        show(listOf(task("One", dueDate = due)), onReturn = { id, q, d -> returned = Triple(id, q, d) })
        rule.onNodeWithText("Return to matrix").performClick()
        rule.onNodeWithText("Do First").performClick()
        rule.onNodeWithTag("saved_return_confirm").performClick()
        assertEquals(Triple("id-One", Quadrant.DO_FIRST, due), returned)
    }

    @Test
    fun returnCanClearTheDate() {
        var returnedDue: Instant? = Instant.EPOCH
        show(listOf(task("One", dueDate = t0)), onReturn = { _, _, d -> returnedDue = d })
        rule.onNodeWithText("Return to matrix").performClick()
        rule.onNodeWithText("None").performClick()
        rule.onNodeWithTag("saved_return_confirm").performClick()
        assertNull(returnedDue)
    }
}
