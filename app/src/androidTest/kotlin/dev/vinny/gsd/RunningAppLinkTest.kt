package dev.vinny.gsd

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.Lifecycle
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
import kotlin.test.assertEquals

/**
 * Review finding 4: a link that reaches a running app must land on the activity the user can see.
 * With no launch mode, each link stacked another MainActivity, and the covered copy took the route.
 */
class RunningAppLinkTest {
    // The v2 rule queues effects as production does, so the route collector runs after the NavHost exists.
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()

    @Before
    fun skipOnboarding() = runBlocking { app.container.prefs.setHasOnboarded(true) }

    /** The scenario closes only the activity it launched. A stacked copy would leak into the next test. */
    @After
    fun finishStrayCopies() {
        val strays = mainActivities()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { strays.forEach { it.finish() } }
    }

    @Test
    fun aLinkToARunningAppDoesNotStackASecondActivity() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitSelected("Matrix")

            send("gsd://dashboard")
            // Either outcome ends the wait: the link stacked a copy, or the one activity handled it.
            rule.waitUntil(5_000) { mainActivities().size > 1 || tabIsSelected("Dashboard") }

            assertEquals(1, mainActivities().size)
        }
    }

    @Test
    fun aLinkToARunningAppNavigatesTheActivityOnScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSelected("Matrix")

            send("gsd://dashboard")
            awaitSelected("Dashboard")

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun aCaptureLinkToARunningAppOpensTheEditorOnScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitSelected("Matrix")

            send("gsd://capture")
            rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isNotEmpty() }

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /** The widget builds this same intent. RemoteViews adds NEW_TASK when it fires one. */
    private fun send(link: String) = app.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(link), app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun mainActivities(): List<MainActivity> {
        val found = mutableListOf<MainActivity>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val registry = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }.forEach { stage ->
                found += registry.getActivitiesInStage(stage).filterIsInstance<MainActivity>()
            }
        }
        return found
    }

    private fun tabIsSelected(label: String) =
        rule.onAllNodes(navItem(label) and isSelected()).fetchSemanticsNodes().isNotEmpty()

    private fun awaitSelected(label: String) = rule.waitUntil(5_000) { tabIsSelected(label) }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
