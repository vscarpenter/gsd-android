package dev.vinny.gsd.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeEntry
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskEditorDepthTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun draft() =
        Task(id = "task-under-edit-00000", title = "t", urgent = false, important = false,
            createdAt = t0, updatedAt = t0)

    private fun editor(draft: Task, allTasks: List<Task> = emptyList(), onEdit: ((Task) -> Task) -> Unit) {
        rule.setContent {
            GsdTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState(),
                    ),
                ) {
                    EditorContent(draft = draft, canSave = true, onEdit = onEdit, onSave = {}, allTasks = allTasks)
                }
            }
        }
    }

    @Test
    fun todayPresetChipSetsDueDate() {
        val base = draft()
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithText("Today").performScrollTo().performClick()
        assertNotNull(edited?.dueDate)
    }

    @Test
    fun repeatSelectionUpdatesRecurrence() {
        val base = draft()
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithText("Weekly").performScrollTo().performClick()
        assertEquals(Recurrence.WEEKLY, edited?.recurrence)
    }

    @Test
    fun subtaskAddCommits() {
        val base = draft()
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithTag("subtask_input").performScrollTo().performTextInput("write the intro,")
        assertEquals("write the intro", edited?.subtasks?.single()?.title)
    }

    @Test
    fun subtaskToggleFlips()  {
        val base = draft().copy(subtasks = listOf(Subtask("sub1", "step one")))
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithText("step one").performScrollTo().performClick()
        assertEquals(true, edited?.subtasks?.single()?.completed)
    }

    @Test
    fun cycleCreatingDependencyIsDisabled() {
        val base = draft()
        // "blocker" already depends on the task under edit → picking it would cycle
        val cyclic = Task(id = "cyclic-candidate-0000", title = "cyclic candidate", urgent = false,
            important = false, createdAt = t0, updatedAt = t0,
            dependencies = listOf(base.id))
        val safe = Task(id = "safe-candidate-000000", title = "safe candidate", urgent = false,
            important = false, createdAt = t0, updatedAt = t0)
        editor(base, allTasks = listOf(base, cyclic, safe)) { }
        rule.onNodeWithTag("dependency_search").performScrollTo().performTextInput("candidate")
        rule.onNodeWithText("safe candidate").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("dep_option_cyclic-candidate-0000").assertIsNotEnabled()
    }

    @Test
    fun estimateOverTrackedShowsAlert() {
        val base = draft().copy(
            estimatedMinutes = 1,
            timeEntries = listOf(TimeEntry("abcd1234", t0, t0.plusSeconds(600))), // 10m tracked
        )
        editor(base) { }
        rule.onNodeWithTag("estimate_over").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun snoozeMenuOffersSixPresets() {
        val base = draft()
        editor(base) { }
        rule.onNodeWithTag("snooze_row").performScrollTo().performClick()
        listOf("15 minutes", "30 minutes", "1 hour", "3 hours", "Tomorrow").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
        // "Next week" also exists as a due-date preset chip; the menu adds a second node
        rule.onAllNodesWithText("Next week").assertCountEquals(2)
    }
}
