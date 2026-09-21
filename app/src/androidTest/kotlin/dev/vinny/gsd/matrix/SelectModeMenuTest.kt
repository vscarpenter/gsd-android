package dev.vinny.gsd.matrix

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 48: in select mode the task menu still opened. Edit and Complete toggled the
 * selection, and the other six items did nothing. A card in select mode has no menu.
 */
class SelectModeMenuTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private val title = "select me"
    private lateinit var taskId: String

    /** These tests write through the real store, so a signed-in device would sync their tasks to the backend. */
    @Before
    fun seedOneTaskInDoFirst() = runBlocking {
        assumeFalse("signed in: refusing to write test tasks", app.container.syncCoordinator.signedIn.value)
        val now = Instant.now()
        taskId = app.container.store.add(
            Task(title = title, urgent = true, important = true, createdAt = now, updatedAt = now),
        ).id
    }

    @After
    fun removeTheTask() = runBlocking {
        app.container.store.delete(taskId)
        app.container.trashStore.deleteForever(taskId)
    }

    @Test
    fun aCardInSelectModeShowsNoOptionsButton() {
        rule.setContent { GsdTheme { MatrixScreen(container = app.container) } }
        rule.waitUntil(5_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        val optionsOnThisCard = hasContentDescription("Task options") and hasAnyAncestor(hasText(title))
        rule.onNode(optionsOnThisCard).assertExists()

        rule.onNodeWithContentDescription("Select tasks").performClick()

        rule.waitUntil(5_000) { rule.onAllNodes(optionsOnThisCard).fetchSemanticsNodes().isEmpty() }
    }
}
