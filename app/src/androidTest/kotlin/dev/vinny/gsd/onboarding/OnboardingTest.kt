package dev.vinny.gsd.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class OnboardingTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fourPagesAdvanceAndStartFinishes() {
        var finished = false
        rule.setContent { GsdTheme { OnboardingFlow(onFinished = { finished = true }) } }
        rule.onNodeWithText("Get the right things done.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Four quadrants, one decision.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Capture in one line.").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Yours, and only yours.").assertIsDisplayed()
        rule.onNodeWithText("Start using GSD").performClick()
        assertEquals(true, finished)
    }

    @Test
    fun skipFinishesEarly() {
        var finished = false
        rule.setContent { GsdTheme { OnboardingFlow(onFinished = { finished = true }) } }
        rule.onNodeWithText("Skip").performClick()
        assertEquals(true, finished)
    }

    /** Review finding 19: the page index lived in `remember`, so a rotation went back to page one. */
    @Test
    fun thePageSurvivesRotation() {
        val restoration = StateRestorationTester(rule)
        restoration.setContent { GsdTheme { OnboardingFlow(onFinished = {}) } }
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithContentDescription("Page 2 of 4").assertExists()

        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithContentDescription("Page 2 of 4").assertExists()
        rule.onNodeWithText("Four quadrants, one decision.").assertIsDisplayed()
    }

    /** Review finding 19: the whole column was centered, so Skip and Next moved from page to page. */
    @Test
    fun skipAndNextStayPutFromPageToPage() {
        rule.setContent { GsdTheme { OnboardingFlow(onFinished = {}) } }
        val skip = rule.onNodeWithText("Skip").getBoundsInRoot()
        val next = rule.onNodeWithText("Next").getBoundsInRoot()

        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Four quadrants, one decision.").assertIsDisplayed()

        assertEquals(skip, rule.onNodeWithText("Skip").getBoundsInRoot())
        assertEquals(next, rule.onNodeWithText("Next").getBoundsInRoot())
    }

    /** Spec 8.19: the last page offers sync sign-in beside "Start using GSD". */
    @Test
    fun theLastPageOffersSignIn() {
        var provider: String? = null
        rule.setContent { GsdTheme { OnboardingFlow(onFinished = {}, onSignIn = { provider = it }) } }
        repeat(3) { rule.onNodeWithText("Next").performClick() }

        rule.onNodeWithText("Sign in with Google").performClick()

        assertEquals("google", provider)
        rule.onNodeWithText("Sign in with Apple").assertIsDisplayed()
    }
}
