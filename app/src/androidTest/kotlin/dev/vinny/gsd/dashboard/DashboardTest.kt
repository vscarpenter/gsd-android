package dev.vinny.gsd.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
                    now = now,
                    zone = ZoneOffset.UTC,
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
                    onOpenOverdue = {}, loaded = true, now = now, zone = ZoneOffset.UTC)
            }
        }
        rule.onNodeWithText("No stats yet").assertIsDisplayed()
        rule.onNodeWithText("Complete a few tasks and your trends will appear.").assertIsDisplayed()
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
                    now = now,
                    zone = ZoneOffset.UTC,
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
                    now = now,
                    zone = ZoneOffset.UTC,
                    onOpenTask = {},
                )
            }
        }
        rule.onNodeWithText("No stats yet").assertIsDisplayed()
        rule.onNodeWithContentDescription("Loading").assertDoesNotExist()
    }

    @Test
    fun ringsCardShowsPercentsAndAnEmDashForAnEmptyQuadrant() {
        val tasks = listOf(
            Task(title = "a", urgent = true, important = true, createdAt = now, updatedAt = now),
            Task(title = "b", urgent = true, important = true, completed = true, completedAt = now, createdAt = now, updatedAt = now),
        )
        val stats = Analytics.compute(tasks, 7, now, ZoneOffset.UTC)
        rule.setContent { GsdTheme { DashboardContent(stats, 7, {}, {}, loaded = true, now = now, zone = ZoneOffset.UTC, onOpenTask = {}) } }
        rule.onNodeWithText("Completion by Quadrant").assertIsDisplayed()
        rule.onNodeWithContentDescription("Completion rings, 50% of all tasks complete").assertIsDisplayed()
        rule.onNodeWithContentDescription("Do First: 50% complete").assertExists()
        rule.onNodeWithContentDescription("Schedule: no tasks").assertExists()
        rule.onAllNodesWithContentDescription("no tasks", substring = true).assertCountEquals(3)
    }

    @Test
    fun upcomingRowsShowDateTodayBadgeAndOpenTheEditor() {
        val zone = java.time.ZoneId.of("America/Chicago")
        val nine = java.time.LocalDateTime.of(2026, 6, 3, 9, 0).atZone(zone).toInstant()
        val today = Task(title = "due today", urgent = false, important = true, createdAt = nine, updatedAt = nine,
            dueDate = java.time.LocalDateTime.of(2026, 6, 3, 0, 0).atZone(zone).toInstant())
        val later = Task(title = "due friday", urgent = false, important = true, createdAt = nine, updatedAt = nine,
            dueDate = java.time.LocalDateTime.of(2026, 6, 5, 12, 0).atZone(zone).toInstant())
        val stats = Analytics.compute(listOf(today, later), 7, nine, zone)
        var opened: String? = null
        rule.setContent { GsdTheme { DashboardContent(stats, 7, {}, {}, loaded = true, now = nine, zone = zone, onOpenTask = { opened = it }) } }
        rule.onNodeWithText("Today").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Today").assertCountEquals(1)   // "due friday" carries no badge
        rule.onNodeWithText("due friday").performScrollTo().performClick()
        assertEquals(later.id, opened)
    }
}
