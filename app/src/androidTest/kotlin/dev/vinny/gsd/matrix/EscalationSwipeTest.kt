package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * The Mark urgent swipe button (spec-escalation.md §5). An escalating row opens to three buttons
 * instead of two, so the open distance has to follow the count, or the buttons come up squeezed.
 */
class EscalationSwipeTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val dueTomorrow: Instant =
        LocalDate.now(zone).plusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
    private val now = Instant.now()
    private val task = Task(
        id = "task-under-test-00000", title = "Renew passport", urgent = false, important = true,
        createdAt = now, updatedAt = now, dueDate = dueTomorrow,
    )

    private fun show(window: Int?, onMarkUrgent: (() -> Unit)?, onSnooze: () -> Unit = {}) = rule.setContent {
        GsdTheme {
            CompositionLocalProvider(LocalEscalationWindow provides window) {
                SwipeTaskRow(
                    task = task,
                    onComplete = {},
                    onDelete = {},
                    onSnooze = onSnooze,
                    onMarkUrgent = onMarkUrgent,
                ) {
                    Box(Modifier.fillMaxWidth().height(72.dp).testTag("row"))
                }
            }
        }
    }

    private fun partSwipe(dp: Int) = rule.onNodeWithTag("row").performTouchInput {
        down(centerRight)
        moveBy(Offset(-dp.dp.toPx(), 0f))
        up()
    }

    @Test
    fun anEscalatingRowOpensToThreeFullWidthButtonsAndMarksUrgent() {
        var marks = 0
        show(window = 2, onMarkUrgent = { marks++ })

        partSwipe(150) // past half of three buttons, short of a full swipe
        rule.waitForIdle()

        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onNodeWithText("Snooze").assertIsDisplayed()
        rule.onNodeWithText("Mark urgent").assertIsDisplayed().assertWidthIsAtLeast(70.dp)
        rule.onNodeWithText("Mark urgent").performClick()
        rule.waitForIdle()

        assertEquals(1, marks)
    }

    @Test
    fun aRowThatIsNotEscalatingKeepsTwoButtons() {
        show(window = null, onMarkUrgent = {})

        partSwipe(100)
        rule.waitForIdle()

        rule.onNodeWithText("Snooze").assertIsDisplayed().assertWidthIsAtLeast(70.dp)
        rule.onNodeWithText("Mark urgent").assertDoesNotExist()
    }

    @Test
    fun theFullSwipeStillSnoozesWhenAThirdButtonIsThere() {
        var snoozes = 0
        var marks = 0
        show(window = 2, onMarkUrgent = { marks++ }, onSnooze = { snoozes++ })

        rule.onNodeWithTag("row").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(1, snoozes)
        assertEquals(0, marks)
    }
}
