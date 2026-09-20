package dev.vinny.gsd.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

/** Review finding S1: a session that ended must say so, not look like an ordinary signed-out state. */
class SignedOutAccountTest {
    @get:Rule
    val rule = createComposeRule()

    private val expiredNote = "Session expired. Sign in again to keep syncing."

    private fun show(sessionExpired: Boolean) = rule.setContent {
        GsdTheme { Column { SignedOutAccount(sessionExpired, signInError = null, onProvider = {}) } }
    }

    @Test
    fun anExpiredSessionSaysSoAboveTheSignInOptions() {
        show(sessionExpired = true)
        rule.onNodeWithText(expiredNote).assertIsDisplayed()
        rule.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }

    @Test
    fun anOrdinarySignedOutStateShowsNoExpiredNote() {
        show(sessionExpired = false)
        rule.onNodeWithText(expiredNote).assertDoesNotExist()
        rule.onNodeWithText("Sign in with Google").assertIsDisplayed()
    }
}
