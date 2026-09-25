package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TaskCardLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    /** Review finding 34: the spine was a fixed 40 dp, so it covered only the top of a tall card. */
    @Test
    fun theSpineRunsDownToTheLastRowOfATallCard() {
        val task = Task(
            title = "Renew passport",
            description = "Photos, the renewal form, and the old passport",
            tags = listOf("travel", "errands"),
            subtasks = listOf(
                Subtask(id = "sub-0001", title = "photos", completed = true),
                Subtask(id = "sub-0002", title = "form"),
            ),
            urgent = true,
            important = true,
            createdAt = t0,
            updatedAt = t0,
        )
        rule.setContent { GsdTheme { TaskCard(task = task, onToggleComplete = {}, onClick = {}) } }

        val spine = rule.onNodeWithTag("card_spine", useUnmergedTree = true).getBoundsInRoot()
        val progress = rule.onNodeWithText("1/2", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("spine ends at ${spine.bottom}, progress at ${progress.bottom}", spine.bottom >= progress.bottom)
    }

    /** Review finding 35: at font scale 2.0 a title stopped at two lines with an ellipsis. */
    @Test
    fun aLongTitleIsNotCutOffAtFontScaleTwo() {
        val task = Task(
            title = "Renew passport before the October trip to Lisbon with the whole family",
            createdAt = t0,
            updatedAt = t0,
        )
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                GsdTheme { TaskCard(task = task, onToggleComplete = {}, onClick = {}) }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(task.title, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertFalse("the title was cut off", layouts.single().hasVisualOverflow)
    }

    /** Review, Copy: the due labels were English literals in Kotlin. They come from resources now. */
    @Test
    fun dueLabelsNameTheDay() {
        fun dueIn(days: Long) = Task(
            title = "due in $days",
            dueDate = ZonedDateTime.now().plusDays(days).toInstant(),
            createdAt = t0,
            updatedAt = t0,
        )
        rule.setContent {
            GsdTheme {
                Column {
                    listOf(-2L, 0L, 1L, 3L).forEach { TaskCard(task = dueIn(it), onToggleComplete = {}, onClick = {}) }
                }
            }
        }
        listOf("Overdue", "Due today", "Due tomorrow", "in 3 days").forEach {
            rule.onNodeWithText(it, useUnmergedTree = true).assertExists()
        }
    }
}
