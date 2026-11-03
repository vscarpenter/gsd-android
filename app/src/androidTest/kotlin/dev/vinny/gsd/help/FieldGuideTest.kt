package dev.vinny.gsd.help

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import dev.vinny.gsd.theme.GsdTheme
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 20: the Field Guide was one paragraph with no scroll container, and it still said
 * to sign in "when it arrives" long after sync shipped. Spec 8.19 lists its sections.
 */
class FieldGuideTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theGuideScrollsThroughItsSectionsToTheSiteLink() {
        rule.setContent { GsdTheme { FieldGuideScreen() } }

        rule.onNodeWithText("How to use GSD").assertIsDisplayed()
        rule.onNodeWithText("!! ship the deck #work #q2").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Neither: noise. Stop doing these.").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Read more at gsdtaskmanager.com").performScrollTo().assertHasClickAction()
        rule.onAllNodesWithText("when it arrives", substring = true).assertCountEquals(0)
    }

    /** The 2026-11-02 docs review: the guide skipped Saved, escalation, Trash, search, and bulk select. */
    @Test
    fun theGuideCoversTheFeaturesShippedSince0100() {
        rule.setContent { GsdTheme { FieldGuideScreen() } }

        rule.onNodeWithText("https://…").performScrollTo().assertIsDisplayed()
        listOf("Due dates", "Reminders", "Escalating", "Saved", "Undo and Trash", "Search", "Smart Views", "Select several")
            .forEach { title -> rule.onNodeWithText(title).performScrollTo().assertIsDisplayed() }
        rule.onNodeWithText("Pull down on the Matrix to sync now.", substring = true).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("lists every run", substring = true).assertCountEquals(0)
    }
}
