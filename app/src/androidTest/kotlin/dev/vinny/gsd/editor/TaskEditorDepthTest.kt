package dev.vinny.gsd.editor

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    /** The body scrolls its own sections under the pinned header (finding 25), so no outer scroll. */
    private fun editor(draft: Task, allTasks: List<Task> = emptyList(), onEdit: ((Task) -> Task) -> Unit) {
        rule.setContent {
            GsdTheme {
                EditorContent(draft = draft, canSave = true, onEdit = onEdit, onSave = {}, allTasks = allTasks)
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

    @Test
    fun offListStoredReminderRendersAndSurvivesAnUntouchedSave() {
        val stored = Task(title = "synced", urgent = true, important = true, createdAt = t0, updatedAt = t0,
            dueDate = t0.plusSeconds(86_400), notifyBefore = 45)
        var saved: Task? = null
        rule.setContent { GsdTheme { ReminderSection(draft = stored, onEdit = { edit -> saved = edit(stored) }) } }
        rule.onNodeWithTag("reminder_row").assertTextEquals("45 minutes before")
        rule.onNodeWithTag("reminder_row").performClick()
        rule.onNodeWithText("30 minutes before").assertIsDisplayed()
        // "45 minutes before" also names the current row label, so scope to the open dropdown's popup
        val offListOption = hasText("45 minutes before").and(hasAnyAncestor(isPopup()))
        rule.onNode(offListOption).assertIsDisplayed()                 // offered in sorted position
        rule.onNodeWithText("1 hour before").assertIsDisplayed()
        rule.onNode(offListOption).performClick()
        assertEquals(45, saved?.notifyBefore)                          // choosing it again changes nothing
    }

    /**
     * B-36: a task with no offset fires the default reminder on this device (ReminderPlanner), so the
     * row can't say "None". It says what this device does and promises nothing about other devices.
     */
    @Test
    fun aTaskWithNoReminderOffsetIsLabelledAsTheDefaultReminder() {
        val stored = Task(title = "x", urgent = true, important = true, createdAt = t0, updatedAt = t0, dueDate = t0, notifyBefore = null)
        var saved: Task? = Task(title = "marker", urgent = true, important = true, createdAt = t0, updatedAt = t0, notifyBefore = 30)
        rule.setContent { GsdTheme { ReminderSection(draft = stored, onEdit = { edit -> saved = edit(stored) }) } }
        rule.onNodeWithTag("reminder_row").assertTextEquals("Default reminder")
        rule.onNodeWithTag("reminder_row").performClick()
        rule.onAllNodesWithText("None").assertCountEquals(0)
        rule.onNode(hasText("Default reminder").and(hasAnyAncestor(isPopup()))).performClick()
        assertEquals(null, saved?.notifyBefore) // the same null as before: the wire doesn't change
    }

    @Test
    fun reminderLabelsFollowTheSharedFormula() {
        val stored = Task(title = "x", urgent = true, important = true, createdAt = t0, updatedAt = t0, dueDate = t0, notifyBefore = 2160)
        rule.setContent { GsdTheme { ReminderSection(draft = stored, onEdit = {}) } }
        rule.onNodeWithTag("reminder_row").assertTextEquals("36 hours before")
    }

    /** Review finding 30: the preset chips never showed which one the due date matched. */
    @Test
    fun thePresetMatchingTheDueDateShowsAsSelected() {
        val zone = java.time.ZoneId.systemDefault()
        // Next week is strictly after today, so the two chips can never both match
        val nextMonday = dev.vinny.gsd.model.DuePreset.NEXT_WEEK.resolve(java.time.LocalDate.now(zone))!!
        val base = draft().copy(dueDate = nextMonday.atStartOfDay(zone).toInstant())
        editor(base) {}
        rule.onNodeWithText("Next week").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Today").assertIsNotSelected()
    }

    /** Review finding 6: the presets were the only way to set a due date. */
    @Test
    fun theDateRowOpensADatePickerThatSetsTheDate() {
        val base = draft()
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithTag("due_date_row").performScrollTo().performClick()
        rule.onNodeWithText("Done").performClick() // the picker opens on today
        val zone = java.time.ZoneId.systemDefault()
        assertEquals(java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant(), edited?.dueDate)
    }

    @Test
    fun allDayClearsATimeOfDay() {
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.LocalDate.now(zone).plusDays(3)
        val base = draft().copy(dueDate = date.atTime(15, 30).atZone(zone).toInstant())
        var edited: Task? = null
        editor(base) { edited = it(base) }
        rule.onNodeWithTag("due_time_row").performScrollTo().performClick()
        rule.onNodeWithText("All day").performClick()
        assertEquals(date.atStartOfDay(zone).toInstant(), edited?.dueDate)
    }

    /** Review finding 30: subtask toggles were sunken discs with no checkbox role or state. */
    @Test
    fun aSubtaskIsACheckboxWithItsState() {
        val base = draft().copy(subtasks = listOf(Subtask(id = "sub-0001", title = "photos")))
        var edited: Task? = null
        editor(base) { edited = it(base) }

        rule.onNodeWithText("photos").performScrollTo().assertIsOff()
        rule.onNodeWithText("photos").performClick()

        assertEquals(true, edited?.subtasks?.single()?.completed)
    }

    /** Review finding 30: the estimate showed a bare "90" with no unit. */
    @Test
    fun theEstimateShowsItsUnitAndLength() {
        editor(draft().copy(estimatedMinutes = 90)) {}

        rule.onNodeWithText("min").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1h 30m", substring = true).assertIsDisplayed()
    }

    /** Review finding 30: the tag row had no cap, so a 21st tag failed only at Save. */
    @Test
    fun theTagFieldGoesAwayAtTwentyTags() {
        editor(draft().copy(tags = (1..20).map { "tag$it" })) {}

        rule.onNodeWithTag("editor_tag_input").assertDoesNotExist()
        rule.onNodeWithText("A task holds at most 20 tags.").performScrollTo().assertIsDisplayed()
    }

    /** Review finding 30: the tag chip stood 48 dp tall around a small icon. The whole chip removes the tag. */
    @Test
    fun tappingATagChipRemovesTheTag() {
        val base = draft().copy(tags = listOf("work"))
        var edited: Task? = null
        editor(base) { edited = it(base) }

        rule.onNodeWithContentDescription("Remove tag work").performScrollTo().performClick()

        assertEquals(emptyList(), edited?.tags)
    }
}
