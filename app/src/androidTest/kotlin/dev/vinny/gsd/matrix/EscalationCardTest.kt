package dev.vinny.gsd.matrix

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Escalation on the card (spec-escalation.md §5): one marker, one action, and Off removes both. */
@OptIn(ExperimentalTestApi::class)
class EscalationCardTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val tomorrow: Instant =
        java.time.LocalDate.now(zone).plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
    private val now = Instant.now()

    private fun task(urgent: Boolean = false, important: Boolean = true) = Task(
        id = "task-under-test-00000", title = "Renew passport", urgent = urgent, important = important,
        createdAt = now, updatedAt = now, dueDate = tomorrow,
    )

    private fun show(window: Int?, task: Task, actions: TaskRowActions = TaskRowActions()) = rule.setContent {
        GsdTheme {
            CompositionLocalProvider(LocalEscalationWindow provides window) {
                TaskRowWithMenu(task = task, badges = TaskBadges(0, 0), actions = actions)
            }
        }
    }

    @Test
    fun anEscalatingCardShowsTheTagWithItsAccessibleName() {
        show(window = 2, task = task())
        rule.onNodeWithTag("escalation_tag").assertExists()
        rule.onNode(hasContentDescription("Escalating: due tomorrow, not marked urgent.")).assertExists()
    }

    @Test
    fun offRemovesTheTag() {
        show(window = null, task = task())
        rule.onNodeWithTag("escalation_tag").assertDoesNotExist()
    }

    @Test
    fun anUrgentTaskIsNotMarked() {
        show(window = 2, task = task(urgent = true))
        rule.onNodeWithTag("escalation_tag").assertDoesNotExist()
    }

    @Test
    fun markUrgentIsInTheMenuWithItsHintAndRuns() {
        val ran = mutableListOf<String>()
        show(window = 2, task = task(), actions = TaskRowActions(onMarkUrgent = { ran += "urgent" }))
        rule.onNode(hasText("Renew passport") and hasClickAction()).performTouchInput { longClick() }
        rule.onNodeWithTag("menu_mark_urgent").assertExists()
        rule.onNode(hasText("Moves to Do First")).assertExists()
        rule.onNodeWithTag("menu_mark_urgent").performClick()
        assertEquals(listOf("urgent"), ran)
    }

    @Test
    fun anEliminateTaskHintsDelegate() {
        show(window = 2, task = task(important = false))
        rule.onNode(hasText("Renew passport") and hasClickAction()).performTouchInput { longClick() }
        rule.onNode(hasText("Moves to Delegate")).assertExists()
    }

    @Test
    fun talkBackGetsAMarkUrgentAction() {
        val ran = mutableListOf<String>()
        show(window = 2, task = task(), actions = TaskRowActions(onMarkUrgent = { ran += "urgent" }))
        rule.onNode(hasText("Renew passport") and hasClickAction()).performCustomAccessibilityActionWithLabel("Mark urgent")
        assertEquals(listOf("urgent"), ran)
    }
}
