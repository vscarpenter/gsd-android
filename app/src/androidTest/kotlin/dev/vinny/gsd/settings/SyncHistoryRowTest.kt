package dev.vinny.gsd.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.vinny.gsd.store.SyncHistoryEntity
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

/**
 * The rows printed the engine's raw status ("partial"), a fixed English 24-hour date, and arrows
 * that TalkBack read as symbols (review, Copy and Accessibility).
 */
class SyncHistoryRowTest {
    @get:Rule
    val rule = createComposeRule()

    private fun entry(status: String) = SyncHistoryEntity(
        timestamp = 1_790_000_000_000,
        status = status,
        pushedCount = 3,
        pulledCount = 2,
        conflictsResolved = 1,
        failedCount = null,
        errorMessage = null,
        duration = 120,
        deviceId = "device-1",
        triggeredBy = "user",
    )

    @Test
    fun aRowNamesItsStatusInWords() {
        rule.setContent { GsdTheme { SyncHistoryRow(entry("partial")) } }
        rule.onNodeWithText("Partly synced", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theCountsAreReadAsWords() {
        rule.setContent { GsdTheme { SyncHistoryRow(entry("success")) } }
        rule.onNodeWithContentDescription("3 sent, 2 received, 1 conflict resolved", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
