package dev.vinny.gsd.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import dev.vinny.gsd.dashboard.DashboardSkeleton
import dev.vinny.gsd.browse.TaskListSkeleton
import dev.vinny.gsd.matrix.MatrixSkeleton
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

// A single ComposeTestRule can only set content once per test (this Compose-test BOM throws
// IllegalStateException on a second setContent call), so the three skeletons are split into
// three tests rather than the brief's one-test-three-setContent-calls shape.
class SkeletonTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun dashboardSkeletonIsOneLoadingNode() {
        rule.setContent { GsdTheme { DashboardSkeleton() } }
        rule.onNodeWithContentDescription("Loading").assertIsDisplayed()
    }

    @Test
    fun taskListSkeletonIsOneLoadingNode() {
        rule.setContent { GsdTheme { TaskListSkeleton() } }
        rule.onAllNodesWithContentDescription("Loading").assertCountEquals(1)
    }

    @Test
    fun matrixSkeletonIsOneLoadingNode() {
        rule.setContent { GsdTheme { MatrixSkeleton() } }
        rule.onAllNodesWithContentDescription("Loading").assertCountEquals(1)
    }
}
