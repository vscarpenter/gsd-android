package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Review finding 2: the row painted its swipe background while it sat at rest. An opaque card hid
 * that on Matrix. Trash draws dimmed rows with a bare line of text, so its text sat on solid red.
 */
class SwipeTaskRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private var paper = Color.Unspecified
    private var success = Color.Unspecified

    /** The content paints nothing, like the line of text under a Trash card. */
    private fun showRowOnPaper(
        onComplete: () -> Unit = {},
        onDelete: () -> Unit = {},
        onSnooze: (() -> Unit)? = null,
    ) = rule.setContent {
        GsdTheme {
            paper = LocalGsdColors.current.paper
            success = LocalGsdColors.current.success
            Box(Modifier.background(paper)) {
                SwipeTaskRow(
                    task = Task(
                        id = "task-under-test-00000", title = "at rest", urgent = true, important = true,
                        createdAt = t0, updatedAt = t0,
                    ),
                    onComplete = onComplete,
                    onDelete = onDelete,
                    onSnooze = onSnooze,
                    modifier = Modifier.testTag("row"),
                ) {
                    Spacer(Modifier.fillMaxWidth().height(48.dp))
                }
            }
        }
    }

    @Test
    fun aRowAtRestPaintsNoSwipeBackground() {
        showRowOnPaper()

        val pixels = rule.onNodeWithTag("row").captureToImage().toPixelMap()

        assertEquals(paper.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
    }

    @Test
    fun aSwipeInProgressStillShowsItsBackground() {
        showRowOnPaper()

        rule.onNodeWithTag("row").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.4f, 0f))
        }
        val pixels = rule.onNodeWithTag("row").captureToImage().toPixelMap()
        rule.onNodeWithTag("row").performTouchInput { up() }

        // Left of the check icon, and clear of the rounded corners.
        assertEquals(success.toArgb(), pixels[8, pixels.height / 2].toArgb())
    }

    /** Review finding 3: a second call made the view model forget the task it had just deleted. */
    @Test
    fun aFullTrailingSwipeDeletesOnce() {
        var deletes = 0
        showRowOnPaper(onDelete = { deletes++ })

        rule.onNodeWithTag("row").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun aFullLeadingSwipeCompletesOnce() {
        var completes = 0
        showRowOnPaper(onComplete = { completes++ })

        rule.onNodeWithTag("row").performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertEquals(1, completes)
    }

    /** Spec 8.1: a trailing swipe opens Snooze and Delete. It used to delete, with no snooze. */
    @Test
    fun aPartTrailingSwipeOpensSnoozeAndDelete() {
        var snoozes = 0
        var deletes = 0
        showRowOnPaper(onDelete = { deletes++ }, onSnooze = { snoozes++ })

        rule.onNodeWithTag("row").performTouchInput {
            down(centerRight)
            moveBy(Offset(-100.dp.toPx(), 0f)) // past half of the two buttons, short of a full swipe
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithText("Delete").assertIsDisplayed()
        rule.onNodeWithText("Snooze").assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(1, snoozes)
        assertEquals(0, deletes)
    }

    /** As on iOS, a full trailing swipe runs the edge-most action, Snooze, once. Delete takes a tap. */
    @Test
    fun aFullTrailingSwipeWithSnoozeSnoozesOnce() {
        var snoozes = 0
        var deletes = 0
        showRowOnPaper(onDelete = { deletes++ }, onSnooze = { snoozes++ })

        rule.onNodeWithTag("row").performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(1, snoozes)
        assertEquals(0, deletes)
    }
}
