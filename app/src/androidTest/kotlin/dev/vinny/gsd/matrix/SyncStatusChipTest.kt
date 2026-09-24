package dev.vinny.gsd.matrix

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

class SyncStatusChipTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun hiddenWhenIdleHealthyAndNothingPending() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = true)) } }
        rule.onNodeWithTag("sync_chip").assertDoesNotExist()
    }

    @Test
    fun hiddenEntirelyWhenSignedOut() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = false, pending = 9)) } }
        rule.onNodeWithTag("sync_chip").assertDoesNotExist()
    }

    @Test
    fun showsPendingCountAndProblemStates() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = true, pending = 3)) } }
        rule.onNodeWithContentDescription("3 changes waiting to sync").assertIsDisplayed()
    }

    /** Review, Accessibility: the chip said "Sync status" whatever it showed. */
    @Test
    fun saysWhatItShows() {
        var state by mutableStateOf(SyncChipState(signedIn = true, syncing = true))
        rule.setContent { GsdTheme { SyncStatusChip(state) } }
        rule.onNodeWithContentDescription("Syncing").assertIsDisplayed()

        state = SyncChipState(signedIn = true, failed = 2)
        rule.onNodeWithContentDescription("Some changes couldn't sync").assertIsDisplayed()

        state = SyncChipState(signedIn = true, pending = 1)
        rule.onNodeWithContentDescription("1 change waiting to sync").assertIsDisplayed()
    }
}
