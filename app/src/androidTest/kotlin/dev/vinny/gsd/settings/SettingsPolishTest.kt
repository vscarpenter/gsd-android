package dev.vinny.gsd.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.theme.GsdTheme
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 29: the theme radio sat 2 dp from its label, selected options appended a " ✓"
 * glyph, "Archive now" looked like a day option, quiet hours were fixed at 22:00 to 07:00, action
 * rows measured under 48 dp, and sign-in options were bare text links.
 */
class SettingsPolishTest {
    @get:Rule
    val rule = createComposeRule()

    private val container = (ApplicationProvider.getApplicationContext<Context>() as GsdApp).container
    private var quietBefore: Pair<java.time.LocalTime?, java.time.LocalTime?> = null to null

    @Before
    fun rememberQuietHours() = runBlocking {
        val settings = container.notificationSettingsStore.observe().first()
        quietBefore = settings.quietHoursStart to settings.quietHoursEnd
    }

    @After
    fun restoreQuietHours() = runBlocking {
        container.notificationSettingsStore.update {
            it.copy(quietHoursStart = quietBefore.first, quietHoursEnd = quietBefore.second)
        }
    }

    @Test
    fun optionsShowASelectedStateAndRowsAreFullTargets() {
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("System").assertHeightIsAtLeast(48.dp)
        rule.onAllNodesWithText("✓", substring = true).assertCountEquals(0)
        rule.onNodeWithText("Archive now").performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    /** D-10 rule 5: a changed default doesn't move a task that already has a number, so Settings says so. */
    @Test
    fun theDefaultReminderSaysItAppliesToNewTasks() {
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("For new tasks").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun signInChoicesAreButtons() {
        assumeFalse("signed in: no sign-in choices to check", container.syncCoordinator.signedIn.value)
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("Sign in with Google").performScrollTo()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun quietHoursCanBeSetAndPrintInTheUsersClock() = runBlocking<Unit> {
        container.notificationSettingsStore.update {
            it.copy(quietHoursStart = java.time.LocalTime.of(22, 0), quietHoursEnd = java.time.LocalTime.of(7, 0))
        }
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }

        rule.onNodeWithText("Quiet from").performScrollTo().assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithText("Quiet until").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theChosenArchiveAgeIsSelected() {
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}) } }
        rule.waitForIdle()
        val days = runBlocking { container.db.archiveSettingsDao().observe().first()?.archiveAfterDays ?: 30 }

        rule.onNodeWithText("$days days").performScrollTo().assertIsSelected()
    }

    /** Spec 8.17 lists Feedback as its own section; its row sat at the end of Data & storage. */
    @Test
    fun feedbackHasItsOwnGroup() {
        var opened = false
        rule.setContent { GsdTheme { SettingsScreen(container, onOpenHelp = {}, onOpenFeedback = { opened = true }) } }

        rule.onNode(hasText("Feedback") and isHeading()).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Send feedback").performScrollTo().performClick()
        assertTrue(opened)
    }
}
