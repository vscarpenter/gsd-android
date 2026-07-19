package dev.vinny.gsd

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class LaunchSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavShowsFourDestinationsAndSwitches() {
        rule.onAllNodesWithText("Matrix").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Browse").assertIsDisplayed()
        rule.onNodeWithText("Dashboard").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Appearance", ignoreCase = true).assertIsDisplayed()
    }
}
