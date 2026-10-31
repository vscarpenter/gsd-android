package dev.vinny.gsd.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import dev.vinny.gsd.matrix.LocalEscalationWindow
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test

/** The editor shows the card menu's hint while a task escalates (spec-escalation.md §5), as web and iOS do. */
class EscalationEditorHintTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val now = Instant.now()
    private val dueTomorrow: Instant =
        LocalDate.now(zone).plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()

    private fun draft(urgent: Boolean = false, important: Boolean = true) = Task(
        id = "task-under-edit-00000", title = "t", urgent = urgent, important = important,
        createdAt = now, updatedAt = now, dueDate = dueTomorrow,
    )

    private fun editor(draft: Task, window: Int?) = rule.setContent {
        GsdTheme {
            CompositionLocalProvider(LocalEscalationWindow provides window) {
                EditorContent(draft = draft, canSave = true, onEdit = {}, onSave = {}, allTasks = emptyList())
            }
        }
    }

    @Test
    fun aScheduleTaskHintsDoFirst() {
        editor(draft(), window = 2)
        rule.onNodeWithTag("escalation_editor_hint").performScrollTo().assertTextEquals("Moves to Do First")
    }

    @Test
    fun anEliminateTaskHintsDelegate() {
        editor(draft(important = false), window = 2)
        rule.onNodeWithTag("escalation_editor_hint").performScrollTo().assertTextEquals("Moves to Delegate")
    }

    @Test
    fun offHidesTheHint() {
        editor(draft(), window = null)
        rule.onNodeWithTag("escalation_editor_hint").assertDoesNotExist()
    }

    @Test
    fun anUrgentTaskHasNoHint() {
        editor(draft(urgent = true), window = 2)
        rule.onNodeWithTag("escalation_editor_hint").assertDoesNotExist()
    }
}
