package dev.vinny.gsd.nav

import android.view.KeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.MainActivity
import dev.vinny.gsd.model.Task
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Spec 6.6 keys. Review item S4: Ctrl+N or Ctrl+K pressed off the Matrix tab waited for the next
 * visit to Matrix. They now switch to Matrix and open at once, as `gsd://capture` does. Ctrl+1 to
 * Ctrl+4 were never wired.
 */
class KeyboardShortcutTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private val seeded = mutableListOf<String>()

    @Before
    fun skipOnboarding() = runBlocking { app.container.prefs.setHasOnboarded(true) }

    /** The quadrant test writes through the real store, so a signed-in device would sync its tasks. */
    @After
    fun removeSeededTasks() = runBlocking {
        seeded.forEach { id ->
            app.container.store.delete(id)
            app.container.trashStore.deleteForever(id)
        }
    }

    @Test
    fun ctrlNOnAnotherTabGoesToMatrixAndOpensANewTask() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            goTo("Dashboard")

            pressCtrl(scenario, KeyEvent.KEYCODE_N)

            rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(navItem("Matrix")).assertIsSelected()
        }
    }

    @Test
    fun ctrlKOnAnotherTabGoesToMatrixAndOpensThePalette() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            goTo("Settings")

            pressCtrl(scenario, KeyEvent.KEYCODE_K)

            rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(navItem("Matrix")).assertIsSelected()
        }
    }

    @Test
    fun ctrlFourScrollsTheMatrixToEliminate() {
        assumeFalse("signed in: refusing to write test tasks", app.container.syncCoordinator.signedIn.value)
        runBlocking {
            val now = Instant.now()
            repeat(15) { n ->
                seeded += app.container.store.add(
                    Task(title = "keyboard filler $n", urgent = true, important = true, createdAt = now, updatedAt = now),
                ).id
            }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            rule.waitUntil(5_000) { rule.onAllNodesWithText("keyboard filler 0").fetchSemanticsNodes().isNotEmpty() }

            pressCtrl(scenario, KeyEvent.KEYCODE_4)

            rule.waitUntil(5_000) {
                runCatching { rule.onNodeWithText("Eliminate").assertIsDisplayed() }.isSuccess
            }
        }
    }

    private fun goTo(tab: String) {
        rule.waitUntil(5_000) { rule.onAllNodes(navItem(tab)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(navItem(tab)).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(navItem(tab) and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Through the window, as a hardware keyboard delivers it: a focused view may take it first. */
    private fun pressCtrl(scenario: ActivityScenario<MainActivity>, keyCode: Int) {
        scenario.onActivity { activity ->
            activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, keyCode, 0, KeyEvent.META_CTRL_ON))
            activity.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, keyCode, 0, KeyEvent.META_CTRL_ON))
        }
    }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
