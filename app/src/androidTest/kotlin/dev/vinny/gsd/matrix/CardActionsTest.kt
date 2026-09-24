package dev.vinny.gsd.matrix

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Spec 13.3 promises custom accessibility actions on cards, and the review found none: a TalkBack
 * user had the menu button, and swipes they cannot make.
 */
@OptIn(ExperimentalTestApi::class)
class CardActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private val task = Task(id = "task-under-test-00000", title = "Renew passport", createdAt = t0, updatedAt = t0)

    @Test
    fun aCardOffersCompleteEditSnoozeAndDelete() {
        val ran = mutableListOf<String>()
        rule.setContent {
            GsdTheme {
                TaskRowWithMenu(
                    task = task,
                    badges = TaskBadges(0, 0),
                    actions = TaskRowActions(
                        onEdit = { ran += "edit" },
                        onToggleComplete = { ran += "complete" },
                        onSnooze = { ran += "snooze ${it.toHours()}h" },
                        onDelete = { ran += "delete" },
                    ),
                )
            }
        }
        val card = rule.onNode(hasText("Renew passport") and hasClickAction())
        listOf("Complete", "Edit", "Snooze 1 hour", "Delete").forEach { card.performCustomAccessibilityActionWithLabel(it) }

        assertEquals(listOf("complete", "edit", "snooze 1h", "delete"), ran)
    }

    @Test
    fun aCardInSelectModeOffersNone() {
        rule.setContent {
            GsdTheme { TaskRowWithMenu(task = task, badges = TaskBadges(0, 0), actions = TaskRowActions(), selected = false) }
        }
        rule.onNode(hasText("Renew passport") and isToggleable())
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }
}
