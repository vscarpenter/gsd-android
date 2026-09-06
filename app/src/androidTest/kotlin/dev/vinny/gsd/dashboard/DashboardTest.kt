package dev.vinny.gsd.dashboard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.Analytics
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class DashboardTest {
    @get:Rule
    val rule = createComposeRule()

    private val now = Instant.parse("2026-07-19T12:00:00.000Z")

    @Test
    fun statsRenderAndOverdueBannerNavigates() {
        val tasks = listOf(
            Task(title = "late", urgent = true, important = true, createdAt = now, updatedAt = now,
                dueDate = now.minusSeconds(200_000)),
            Task(title = "done", urgent = false, important = false, completed = true,
                completedAt = now, createdAt = now, updatedAt = now),
        )
        val stats = Analytics.compute(tasks, 7, now, ZoneOffset.UTC)
        var overdueOpened = false
        rule.setContent {
            GsdTheme {
                DashboardContent(
                    stats = stats,
                    window = 7,
                    onWindowChange = {},
                    onOpenOverdue = { overdueOpened = true },
                    loaded = true,
                )
            }
        }
        rule.onNodeWithText("ACTIVE").assertIsDisplayed()
        rule.onNodeWithText("50%").assertIsDisplayed()
        rule.onNodeWithText("1 overdue — review the backlog").performClick()
        assertEquals(true, overdueOpened)
    }

    @Test
    fun emptyStateShows() {
        val stats = Analytics.compute(emptyList(), 7, now, ZoneOffset.UTC)
        rule.setContent {
            GsdTheme {
                DashboardContent(stats = stats, window = 7, onWindowChange = {},
                    onOpenOverdue = {}, loaded = true)
            }
        }
        rule.onNodeWithText("No stats yet").assertIsDisplayed()
    }

    // Split into two tests, not one with setContent called twice: this Compose-test BOM
    // throws IllegalStateException on a second setContent call within the same test.
    @Test
    fun loadingShowsSkeletonNotEmptyState() {
        val stats = Analytics.compute(emptyList(), 7, now, ZoneOffset.UTC)
        rule.setContent {
            GsdTheme {
                DashboardContent(
                    stats = stats,
                    window = 7,
                    onWindowChange = {},
                    onOpenOverdue = {},
                    loaded = false,
                    onOpenTask = {},
                )
            }
        }
        rule.onNodeWithContentDescription("Loading").assertIsDisplayed()
        rule.onNodeWithText("No stats yet").assertDoesNotExist()
    }

    @Test
    fun loadedEmptyShowsEmptyStateNotSkeleton() {
        val stats = Analytics.compute(emptyList(), 7, now, ZoneOffset.UTC)
        rule.setContent {
            GsdTheme {
                DashboardContent(
                    stats = stats,
                    window = 7,
                    onWindowChange = {},
                    onOpenOverdue = {},
                    loaded = true,
                    onOpenTask = {},
                )
            }
        }
        rule.onNodeWithText("No stats yet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }
}
