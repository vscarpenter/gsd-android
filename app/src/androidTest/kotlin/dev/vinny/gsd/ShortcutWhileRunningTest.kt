package dev.vinny.gsd

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 46: a static shortcut fired while GSD is on screen lost its link and landed on
 * Matrix. A shortcut carries NEW_TASK, CLEAR_TASK, and TASK_ON_HOME, so the system destroys the
 * running activity and starts a fresh one. For a moment both exist, and the dying copy took the
 * route from the process-wide pendingRoute before the new copy could.
 */
class ShortcutWhileRunningTest {
    // The v2 rule queues effects as production does, so the route collector runs after the NavHost exists.
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()

    @Before
    fun skipOnboarding() = runBlocking { app.container.prefs.setHasOnboarded(true) }

    /** The scenario closes only the activity it launched. The copy the shortcut started would leak into the next test. */
    @After
    fun finishStrayCopies() {
        val strays = live<Activity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { strays.forEach { it.finish() } }
        rule.waitUntil(5_000) { live<Activity>().isEmpty() }
    }

    @Test
    fun aShortcutFiredWhileTheAppIsOnScreenOpensItsView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSelected("Matrix")
            var original: MainActivity? = null
            scenario.onActivity { original = it }

            sendShortcut("gsd://smart-view/today-focus")

            // CLEAR_TASK destroys the original and starts a fresh copy. Wait until only the fresh copy is
            // left, then until the route has been consumed by whichever copy got it.
            rule.waitUntil(10_000) { live<MainActivity>().let { it.size == 1 && it.single() !== original } }
            rule.waitUntil(5_000) { app.container.palette.pendingRoute.value == null }
            rule.waitUntil(5_000) { onScreen("Today's Focus") }
        }
    }

    /** The other static shortcut. Its route goes through a second shared channel, newTaskRequests. */
    @Test
    fun aCaptureShortcutFiredWhileTheAppIsOnScreenOpensTheEditor() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSelected("Matrix")
            var original: MainActivity? = null
            scenario.onActivity { original = it }

            sendShortcut("gsd://capture")

            rule.waitUntil(10_000) { live<MainActivity>().let { it.size == 1 && it.single() !== original } }
            rule.waitUntil(5_000) { app.container.palette.pendingRoute.value == null }
            rule.waitUntil(5_000) { onScreen("New task") }
        }
    }

    /** The flags are the ones `dumpsys shortcut` reports on both static shortcuts: `flg=0x1000c000`. */
    private fun sendShortcut(link: String) = app.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(link), app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_TASK_ON_HOME),
    )

    private inline fun <reified A : Activity> live(): List<A> {
        val found = mutableListOf<A>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }.forEach { stage ->
                found += registry.getActivitiesInStage(stage).filterIsInstance<A>()
            }
        }
        return found
    }

    /** Between the original's death and the fresh copy's first frame there is no hierarchy to read. */
    private fun onScreen(text: String): Boolean = try {
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    } catch (_: IllegalStateException) {
        false
    }

    private fun awaitSelected(label: String) =
        rule.waitUntil(5_000) { rule.onAllNodes(navItem(label) and isSelected()).fetchSemanticsNodes().isNotEmpty() }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
