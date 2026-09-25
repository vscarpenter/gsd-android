package dev.vinny.gsd.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.matrix.TaskCard
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 12: chip rows did not wrap. At font scale 2.0 the last chips were squeezed into
 * one-letter-wide columns about 200 dp tall. A chip that wraps to the next line stays one line tall.
 */
class ChipWrapTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun atFontScaleTwoOnANarrowPhone(content: @Composable () -> Unit) = rule.setContent {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
            GsdTheme { Box(Modifier.width(320.dp)) { content() } }
        }
    }

    private fun assertOneLineTall(label: String) {
        val bounds = rule.onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("\"$label\" is $height tall", height < 80.dp)
    }

    @Test
    fun theRepeatChipsWrap() {
        atFontScaleTwoOnANarrowPhone {
            RepeatSection(draft = Task(title = "t", urgent = false, important = false, createdAt = t0, updatedAt = t0), onEdit = {})
        }
        listOf("Never", "Daily", "Weekly", "Monthly").forEach(::assertOneLineTall)
    }

    @Test
    fun aCardsTagsWrap() {
        val task = Task(
            title = "t",
            tags = listOf("groceries", "household", "weekend", "errands"),
            urgent = false,
            important = false,
            createdAt = t0,
            updatedAt = t0,
        )
        atFontScaleTwoOnANarrowPhone { TaskCard(task = task, onToggleComplete = {}, onClick = {}) }
        task.tags.forEach { assertOneLineTall("#$it") }
    }
}
