package dev.vinny.gsd.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

/** Settings' About group. Reads the app's container and writes nothing. */
class AboutTest {
    @get:Rule
    val rule = createComposeRule()

    private val container = (ApplicationProvider.getApplicationContext<Context>() as GsdApp).container

    /** Review finding 15: Play's User Data policy wants the privacy policy inside the app. */
    @Test
    fun aboutOffersThePrivacyPolicy() {
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("Privacy policy").performScrollTo().assertHasClickAction()
    }

    /** Review finding 20: About lacked the mark, tagline, site link, and credit. Spec 8.17 adds support. */
    @Test
    fun aboutCarriesTheTaglineBuildSupportSiteAndCredit() {
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("Get the right things done.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("(build ", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Contact support").performScrollTo().assertHasClickAction()
        rule.onNodeWithText("gsdtaskmanager.com").performScrollTo().assertHasClickAction()
        rule.onNodeWithText("Crafted by Vinny Carpenter").performScrollTo().assertIsDisplayed()
    }
}
