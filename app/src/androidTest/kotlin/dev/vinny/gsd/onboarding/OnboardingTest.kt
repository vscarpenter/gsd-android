package dev.vinny.gsd.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
        rule.onNodeWithText("GSD").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("The Matrix").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Capture").assertIsDisplayed()
        rule.onNodeWithText("Next").performClick()
        rule.onNodeWithText("Private by design").assertIsDisplayed()
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
}
