package dev.vinny.gsd.nav

import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotEquals

/**
 * Review finding 43: a deep link or a palette row pushes a screen over Matrix. The tab handler
 * then popped that screen and restored it in the same call, so the Matrix tab did nothing.
 */
class TabNavigationTest {
    // The v2 rule queues effects as production does. The v1 rule runs the deep-link collector
    // inline, before the Scaffold has composed the NavHost and set its graph.
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()

    @Before
    fun skipOnboarding() = runBlocking { app.container.prefs.setHasOnboarded(true) }

    @Test
    fun theMatrixTabWorksAfterADeepLinkOpensASmartView() {
        launch("gsd://smart-view/today-focus").use { // the widget header and the "Today's Focus" shortcut
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Today's Focus").fetchSemanticsNodes().isNotEmpty() }

            rule.onNode(navItem("Matrix")).performClick()
            rule.waitForIdle()

            rule.onNode(navItem("Matrix")).assertIsSelected()
        }
    }

    @Test
    fun theMatrixTabWorksAfterThePaletteOpensDashboard() {
        launch().use {
            awaitSelected("Matrix")
            app.container.palette.requestOpen()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isNotEmpty() }
            // A short query keeps the row on a small screen, and keeps the field from matching "Dashboard".
            rule.onNodeWithTag("palette_query").performTextInput("dashb")
            rule.onNode(hasText("Dashboard") and hasAnyAncestor(isDialog())).performClick()
            awaitSelected("Dashboard")

            rule.onNode(navItem("Matrix")).performClick()
            rule.waitForIdle()

            rule.onNode(navItem("Matrix")).assertIsSelected()
        }
    }

    @Test
    fun aDeepLinkToMatrixAddsNoSecondMatrixEntry() {
        launch("gsd://focus").use { scenario ->
            rule.waitUntil(5_000) { app.container.palette.pendingRoute.value == null } // the router has run
            rule.waitForIdle()

            assertOneBackPressLeavesTheApp(scenario)
        }
    }

    @Test
    fun thePaletteMatrixRowAddsNoSecondMatrixEntry() {
        launch().use { scenario ->
            awaitSelected("Matrix")
            app.container.palette.requestOpen()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithTag("palette_query").performTextInput("matr")
            rule.onNode(hasText("Matrix") and hasAnyAncestor(isDialog())).performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isEmpty() }
            rule.waitForIdle()

            assertOneBackPressLeavesTheApp(scenario)
        }
    }

    @Test
    fun theMatrixScreenStaysLiveAfterADeepLinkToMatrix() {
        launch("gsd://capture").use {
            openAndCloseTheEditor(alreadyRequested = true)
        }
    }

    @Test
    fun theMatrixScreenStaysLiveAfterTheTabReturnsToIt() {
        launch("gsd://smart-view/today-focus").use {
            rule.waitUntil(5_000) { rule.onAllNodesWithText("Today's Focus").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(navItem("Matrix")).performClick()
            awaitSelected("Matrix")

            openAndCloseTheEditor(alreadyRequested = false)
        }
    }

    @Test
    fun aSiblingTabKeepsItsStackAcrossAVisitToMatrix() {
        launch().use {
            awaitSelected("Matrix")
            rule.onNode(navItem("Browse")).performClick()
            rule.onNodeWithText("Archive").performClick()
            rule.waitUntil(5_000) {
                rule.onAllNodesWithContentDescription("Archive options").fetchSemanticsNodes().isNotEmpty()
            }

            rule.onNode(navItem("Matrix")).performClick()
            awaitSelected("Matrix")
            rule.onNode(navItem("Browse")).performClick()
            rule.waitForIdle()

            rule.onNodeWithContentDescription("Archive options").assertExists()
        }
    }

    private fun launch(deepLink: String? = null): ActivityScenario<MainActivity> {
        val intent = Intent(app, MainActivity::class.java)
        if (deepLink != null) intent.setAction(Intent.ACTION_VIEW).setData(Uri.parse(deepLink))
        return ActivityScenario.launch(intent)
    }

    /**
     * A Matrix entry stuck below STARTED stops every lifecycle-aware collector on the screen. The
     * sheet then opens, but it never goes away, because nothing sees the draft turn null.
     */
    private fun openAndCloseTheEditor(alreadyRequested: Boolean) {
        if (!alreadyRequested) app.container.palette.requestNewTask()
        rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithContentDescription("Close sheet").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isEmpty() }
    }

    /** A second Matrix entry would swallow the press and leave the activity resumed. */
    private fun assertOneBackPressLeavesTheApp(scenario: ActivityScenario<MainActivity>) {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        val deadline = System.currentTimeMillis() + 5_000
        while (scenario.state == Lifecycle.State.RESUMED && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertNotEquals(Lifecycle.State.RESUMED, scenario.state)
    }

    private fun awaitSelected(label: String) =
        rule.waitUntil(5_000) { rule.onAllNodes(navItem(label) and isSelected()).fetchSemanticsNodes().isNotEmpty() }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
