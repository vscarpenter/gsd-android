package dev.vinny.gsd

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/**
 * Review finding S4: a rotation recreates the activity with the same launch intent. The deep link
 * already ran once, so it must not pull the user back to Matrix and reopen the editor.
 */
class LaunchIntentTest {
    // The v2 rule queues effects as production does. The v1 rule runs them inline, so the deep-link
    // collector would call navigate before the Scaffold has composed the NavHost and set its graph.
    @get:Rule
    val rule = createEmptyComposeRule()

    @Test
    fun aRecreatedActivityDoesNotRunTheLaunchDeepLinkAgain() {
        val app = ApplicationProvider.getApplicationContext<GsdApp>()
        runBlocking { app.container.prefs.setHasOnboarded(true) }
        val capture = Intent(Intent.ACTION_VIEW, Uri.parse("gsd://capture"), app, MainActivity::class.java)

        ActivityScenario.launch<MainActivity>(capture).use { scenario ->
            rule.waitUntil(5_000) { rule.onAllNodesWithText("New task").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Close sheet").performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(5_000) { rule.onAllNodesWithText("New task").fetchSemanticsNodes().isEmpty() }
            rule.onNode(navItem("Settings")).performClick()

            scenario.recreate() // what a rotation does
            rule.waitForIdle()

            rule.onAllNodesWithText("New task").assertCountEquals(0)
            rule.onNode(navItem("Settings")).assertIsSelected()
        }
    }

    /**
     * `singleTask` keeps the last link as the task's base intent. After a reboot, Recents starts the
     * activity fresh with that link and FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY. It already ran once.
     */
    @Test
    fun aLaunchFromRecentsDoesNotRunTheOldLinkAgain() {
        val app = ApplicationProvider.getApplicationContext<GsdApp>()
        runBlocking { app.container.prefs.setHasOnboarded(true) }
        val fromRecents = Intent(Intent.ACTION_VIEW, Uri.parse("gsd://dashboard"), app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

        ActivityScenario.launch<MainActivity>(fromRecents).use {
            rule.waitUntil(5_000) { rule.onAllNodes(navItem("Matrix")).fetchSemanticsNodes().isNotEmpty() }
            rule.waitForIdle()

            rule.onNode(navItem("Matrix")).assertIsSelected()
        }
    }

    /**
     * The onboarding flag read as "done" until it loaded, so on a first launch the nav host could
     * take the link for a frame, and onboarding then replaced it. The link waits for onboarding now.
     */
    @Test
    fun aFirstLaunchLinkSurvivesOnboarding() {
        val app = ApplicationProvider.getApplicationContext<GsdApp>()
        runBlocking { app.container.prefs.setHasOnboarded(false) }
        val dashboard = Intent(Intent.ACTION_VIEW, Uri.parse("gsd://dashboard"), app, MainActivity::class.java)

        try {
            ActivityScenario.launch<MainActivity>(dashboard).use {
                rule.waitUntil(5_000) { rule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty() }
                rule.onAllNodesWithText("Skip")[0].performClick()

                rule.waitUntil(5_000) {
                    rule.onAllNodes(navItem("Dashboard") and isSelected()).fetchSemanticsNodes().isNotEmpty()
                }
            }
        } finally {
            runBlocking { app.container.prefs.setHasOnboarded(true) }
        }
    }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
