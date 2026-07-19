package dev.vinny.gsd.matrix

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

class SyncStatusChipTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun hiddenWhenIdleHealthyAndNothingPending() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = true)) } }
        rule.onNodeWithContentDescription("Sync status").assertDoesNotExist()
    }

    @Test
    fun hiddenEntirelyWhenSignedOut() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = false, pending = 9)) } }
        rule.onNodeWithContentDescription("Sync status").assertDoesNotExist()
    }

    @Test
    fun showsPendingCountAndProblemStates() {
        rule.setContent { GsdTheme { SyncStatusChip(SyncChipState(signedIn = true, pending = 3)) } }
        rule.onNodeWithContentDescription("Sync status").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }
}
