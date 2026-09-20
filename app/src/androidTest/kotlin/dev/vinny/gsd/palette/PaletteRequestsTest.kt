package dev.vinny.gsd.palette

import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Review finding S4: a request opens its surface once. Coming back to the Matrix tab must not replay it. */
class PaletteRequestsTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as GsdApp).container

    @Before
    fun skipOnboarding() {
        runBlocking { container.prefs.setHasOnboarded(true) }
        rule.waitUntil(5_000) { rule.onAllNodes(navItem("Browse")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aNewTaskRequestOpensTheEditorOnlyOnce() {
        container.palette.requestNewTask() // what Ctrl+N and gsd://capture send

        rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isNotEmpty() }
        // The sheet covers the scrim's center on a small screen, so a touch there would miss.
        rule.onNodeWithContentDescription("Close sheet").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(5_000) { rule.onAllNodesWithText("New Task").fetchSemanticsNodes().isEmpty() }

        leaveTheMatrixTabAndReturn()

        rule.onAllNodesWithText("New Task").assertCountEquals(0)
    }

    @Test
    fun aPaletteRequestOpensThePaletteOnlyOnce() {
        container.palette.requestOpen() // what Ctrl+K and Ctrl+F send

        rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isNotEmpty() }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("palette_query").fetchSemanticsNodes().isEmpty() }

        leaveTheMatrixTabAndReturn()

        rule.onAllNodesWithTag("palette_query").assertCountEquals(0)
    }

    private fun leaveTheMatrixTabAndReturn() {
        rule.onNode(navItem("Browse")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(navItem("Browse") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(navItem("Matrix")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(navItem("Matrix") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
