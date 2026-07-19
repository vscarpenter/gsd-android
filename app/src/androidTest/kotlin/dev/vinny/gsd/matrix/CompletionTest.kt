package dev.vinny.gsd.matrix

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class CompletionTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun leadingFullSwipeCompletes() {
        val task = Task(title = "swipe me", urgent = true, important = true, createdAt = t0, updatedAt = t0)
        var completed: String? = null
        rule.setContent {
            GsdTheme {
                SwipeTaskRow(
                    task = task,
                    onComplete = { completed = task.id },
                    onDelete = {},
                ) {
                    TaskCard(task = task, onToggleComplete = {}, onClick = {})
                }
            }
        }
        rule.onNodeWithText("swipe me").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertEquals(task.id, completed)
    }
}
