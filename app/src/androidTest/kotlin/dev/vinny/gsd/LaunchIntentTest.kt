package dev.vinny.gsd

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
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
            rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithContentDescription("Close sheet").performSemanticsAction(SemanticsActions.OnClick)
            rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isEmpty() }
            rule.onNode(navItem("Settings")).performClick()

            scenario.recreate() // what a rotation does
            rule.waitForIdle()

            rule.onAllNodesWithText("New Task").assertCountEquals(0)
            rule.onNode(navItem("Settings")).assertIsSelected()
        }
    }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
