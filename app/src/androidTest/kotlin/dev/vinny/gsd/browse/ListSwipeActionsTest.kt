package dev.vinny.gsd.browse

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 49: a smart view list and Browse search results drew their rows with no swipe
 * gestures. Spec 8.13 calls them "§8.1 rows", and 8.1 gives a row both swipes.
 */
class ListSwipeActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private val title = "swipe me on a list"
    private lateinit var taskId: String

    /** The search field holds the same text on Browse, so the row is the match that takes no typing. */
    private val row = hasText(title) and !hasSetTextAction()

    /**
     * Urgent and important, so the built-in Today's Focus view lists it. These tests write through
     * the real store, so a signed-in device would sync their tasks to the backend.
     */
    @Before
    fun seedOneTask() = runBlocking {
        assumeFalse("signed in: refusing to write test tasks", app.container.syncCoordinator.signedIn.value)
        removeEveryCopy() // a killed run can leave one behind, and two rows would match the same finder
        val now = Instant.now()
        taskId = app.container.store.add(
            Task(title = title, urgent = true, important = true, createdAt = now, updatedAt = now),
        ).id
    }

    @After
    fun cleanUp() = runBlocking { removeEveryCopy() }

    private suspend fun removeEveryCopy() {
        val seeded = if (::taskId.isInitialized) listOf(taskId) else emptyList()
        val titled = app.container.store.observeTasks().first().filter { it.title == title }.map { it.id }
        (titled + seeded).forEach { id ->
            app.container.store.delete(id)
            app.container.trashStore.deleteForever(id)
        }
    }

    @Test
    fun aLeadingSwipeOnASmartViewListCompletesTheTask() {
        showTodaysFocus()

        rule.onNode(row).performTouchInput { swipeRight() }

        rule.waitUntil(5_000) { stored()?.completed == true }
    }

    @Test
    fun aTrailingSwipeOnASmartViewListOffersDelete() {
        showTodaysFocus()

        swipeOpenAndTapDelete()

        rule.waitUntil(5_000) { stored() == null }
    }

    @Test
    fun aLeadingSwipeOnBrowseSearchResultsCompletesTheTask() {
        showBrowseSearchResults()

        rule.onNode(row).performTouchInput { swipeRight() }

        rule.waitUntil(5_000) { stored()?.completed == true }
    }

    @Test
    fun aTrailingSwipeOnBrowseSearchResultsOffersDelete() {
        showBrowseSearchResults()

        swipeOpenAndTapDelete()

        rule.waitUntil(5_000) { stored() == null }
    }

    /** A part swipe opens Snooze and Delete (spec 8.1); a full one would snooze. */
    private fun swipeOpenAndTapDelete() {
        rule.onNode(row).performTouchInput {
            down(centerRight)
            moveBy(Offset(-100.dp.toPx(), 0f)) // past half of the two buttons, short of a full swipe
            up()
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("Delete")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Delete").performClick()
    }

    private fun showTodaysFocus() {
        rule.setContent { GsdTheme { SmartViewListScreen(app.container, "today-focus") } }
        awaitTheRow()
    }

    private fun showBrowseSearchResults() {
        rule.setContent {
            GsdTheme {
                BrowseScreen(
                    container = app.container,
                    onOpenArchive = {}, onOpenTrash = {}, onOpenView = {}, onNewView = {}, onEditView = {},
                )
            }
        }
        rule.onNodeWithTag("browse_search").performTextInput(title)
        awaitTheRow()
    }

    private fun awaitTheRow() =
        rule.waitUntil(5_000) { rule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }

    private fun stored(): Task? = runBlocking { app.container.store.get(taskId) }
}
