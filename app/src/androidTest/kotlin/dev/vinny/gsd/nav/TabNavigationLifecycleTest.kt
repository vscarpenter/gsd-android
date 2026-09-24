package dev.vinny.gsd.nav

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review finding 43: an entry that stays below STARTED stops every lifecycle-aware collector on
 * its screen. launchSingleTop on the visible Matrix entry did exactly that.
 */
class TabNavigationLifecycleTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var archive: Probe

    /** Records when Navigation lets go of the pushed screen's view model. */
    class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun everyTabIsResumedAfterItIsTappedTwice() {
        host()
        Destination.entries.forEach { destination ->
            repeat(2) {
                rule.runOnUiThread { nav.navigateToTab(destination) }
                rule.waitForIdle()
            }
            assertTopIsResumed(destination.route)
        }
    }

    @Test
    fun matrixIsResumedAfterTheTabPopsAPushedScreen() {
        host()
        rule.runOnUiThread { nav.navigate("archive") } // what a deep link or a palette row does
        rule.waitForIdle()

        rule.runOnUiThread { nav.navigateToTab(Destination.MATRIX) }
        rule.waitForIdle()

        assertTopIsResumed(Destination.MATRIX.route)
    }

    /** Review finding 21: the bar compared routes, so a child screen had no tab selected. */
    @Test
    fun aChildScreenBelongsToTheTabBelowIt() {
        host()
        rule.runOnUiThread {
            nav.navigateToTab(Destination.BROWSE)
            nav.navigate("archive")
        }
        rule.waitForIdle()

        rule.runOnUiThread { assertEquals(Destination.BROWSE, nav.owningTab()) }
    }

    @Test
    fun aScreenPushedStraightOverMatrixBelongsToMatrix() {
        host()
        rule.runOnUiThread { nav.navigate("archive") } // what a deep link or a palette row does
        rule.waitForIdle()

        rule.runOnUiThread { assertEquals(Destination.MATRIX, nav.owningTab()) }
    }

    /**
     * Follow-up to finding 43: the tab handler popped a screen pushed straight over Matrix with
     * saveState. Nothing ever restores such a screen, so its view model lived until the activity ended.
     */
    @Test
    fun theMatrixTabLetsGoOfAScreenPushedStraightOverMatrix() {
        host()
        rule.runOnUiThread { nav.navigate("archive") }
        rule.waitForIdle()

        rule.runOnUiThread { nav.navigateToTab(Destination.MATRIX) }
        rule.waitForIdle()

        assertTrue(archive.cleared, "the pushed screen's view model is still held")
    }

    @Test
    fun anotherTabLetsGoOfAScreenPushedStraightOverMatrix() {
        host()
        rule.runOnUiThread { nav.navigate("archive") }
        rule.waitForIdle()

        rule.runOnUiThread { nav.navigateToTab(Destination.BROWSE) }
        rule.waitForIdle()

        assertTrue(archive.cleared, "the pushed screen's view model is still held")
    }

    /** The other side of the same rule: a tab's own stack is still kept for its return. */
    @Test
    fun aTabsOwnStackIsKeptAcrossAVisitToMatrix() {
        host()
        rule.runOnUiThread {
            nav.navigateToTab(Destination.BROWSE)
            nav.navigate("archive")
        }
        rule.waitForIdle()

        rule.runOnUiThread { nav.navigateToTab(Destination.MATRIX) }
        rule.waitForIdle()

        assertFalse(archive.cleared, "Browse's stack was dropped")
    }

    private fun host() = rule.setContent {
        nav = rememberNavController()
        NavHost(nav, startDestination = Destination.MATRIX.route) {
            Destination.entries.forEach { destination -> composable(destination.route) { Text(destination.route) } }
            composable("archive") {
                archive = viewModel { Probe() }
                Text("archive")
            }
        }
    }

    private fun assertTopIsResumed(route: String) = rule.runOnUiThread {
        assertEquals(route, nav.currentDestination?.route)
        assertEquals(Lifecycle.State.RESUMED, nav.currentBackStackEntry?.lifecycle?.currentState, "entry for $route")
    }
}
