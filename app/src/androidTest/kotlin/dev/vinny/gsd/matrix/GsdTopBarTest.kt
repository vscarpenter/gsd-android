package dev.vinny.gsd.matrix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class GsdTopBarTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lockupShowsBrandAndScreenName() {
        rule.setContent {
            GsdTheme {
                GsdTopBar(screenName = "Matrix", showCompleted = true, onToggleShowCompleted = {}, onSearch = {})
            }
        }
        rule.onNodeWithText("GSD").assertIsDisplayed()
        rule.onNodeWithText("Matrix").assertIsDisplayed()
    }

    @Test
    fun eyeToggleFlipsShowCompleted() {
        var toggled = false
        rule.setContent {
            GsdTheme {
                GsdTopBar(
                    screenName = "Matrix",
                    showCompleted = true,
                    onToggleShowCompleted = { toggled = true },
                    onSearch = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Hide completed").performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun buildSectionsHidesCompletedButKeepsActiveCount() {
        val t0 = java.time.Instant.parse("2026-07-19T12:00:00.000Z")
        val tasks = listOf(
            dev.vinny.gsd.model.Task(
                title = "done", urgent = true, important = true, completed = true,
                createdAt = t0, updatedAt = t0,
            ),
            dev.vinny.gsd.model.Task(
                title = "open", urgent = true, important = true,
                createdAt = t0, updatedAt = t0,
            ),
        )
        val q1 = buildSections(tasks, showCompleted = false).first()
        assertEquals(listOf("open"), q1.tasks.map { it.title })
        assertEquals(1, q1.activeCount)
    }
}
