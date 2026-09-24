package dev.vinny.gsd.settings

import android.content.Context
import androidx.compose.ui.test.assertHasClickAction
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
}
