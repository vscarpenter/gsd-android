package dev.vinny.gsd.browse

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 48: outside Matrix the task menu showed eight items, and only Edit and Complete
 * did anything. The other six closed the menu and changed nothing.
 */
class ListMenuActionsTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private val title = "menu me on a list"
    private lateinit var taskId: String

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

    /** Duplicate leaves a second task with the same title, so clear by title and not by id alone. */
    @After
    fun cleanUp() = runBlocking { removeEveryCopy() }

    private suspend fun removeEveryCopy() {
        val seeded = if (::taskId.isInitialized) listOf(taskId) else emptyList()
        (tasksTitled().map { it.id } + seeded).forEach { id ->
            app.container.store.delete(id)
            app.container.trashStore.deleteForever(id)
        }
    }

    @Test
    fun deleteOnASmartViewListDeletesTheTask() {
        showTodaysFocus()

        chooseFromTheMenu("Delete")

        rule.waitUntil(5_000) { stored() == null }
    }

    @Test
    fun deleteOnASmartViewListOffersUndoAndUndoRestoresTheTask() {
        showTodaysFocus()

        chooseFromTheMenu("Delete")
        awaitText("Undo")
        rule.onNodeWithText("Undo").performClick()

        rule.waitUntil(5_000) { stored() != null }
    }

    @Test
    fun duplicateOnASmartViewListAddsACopy() {
        showTodaysFocus()
        assertEquals(1, tasksTitled().size) // or a leftover copy could satisfy the wait below

        chooseFromTheMenu("Duplicate")

        rule.waitUntil(5_000) { tasksTitled().size == 2 }
    }

    @Test
    fun startTimerOnASmartViewListStartsTheTimer() {
        showTodaysFocus()

        chooseFromTheMenu("Start timer")

        rule.waitUntil(5_000) { stored()?.let(TimeTracking::runningEntry) != null }
    }

    @Test
    fun snoozeOnASmartViewListSnoozesTheTask() {
        showTodaysFocus()

        chooseFromTheMenu("Snooze")
        rule.onNodeWithText("1 hour").performClick()

        rule.waitUntil(5_000) { stored()?.snoozedUntil != null }
    }

    @Test
    fun moveToOnASmartViewListChangesTheQuadrant() {
        showTodaysFocus()

        chooseFromTheMenu("Move to")
        rule.onNodeWithText("Schedule").performClick()

        rule.waitUntil(5_000) { stored()?.quadrant == Quadrant.SCHEDULE }
    }

    @Test
    fun deleteOnBrowseSearchResultsDeletesTheTask() {
        rule.setContent {
            GsdTheme {
                BrowseScreen(
                    container = app.container,
                    onOpenArchive = {}, onOpenTrash = {}, onOpenView = {}, onNewView = {}, onEditView = {},
                )
            }
        }
        rule.onNodeWithTag("browse_search").performTextInput(title)

        chooseFromTheMenu("Delete")

        rule.waitUntil(5_000) { stored() == null }
    }

    private fun showTodaysFocus() {
        rule.setContent { GsdTheme { SmartViewListScreen(app.container, "today-focus") } }
        awaitText(title)
    }

    /** Other tests may leave tasks in the view, so open the menu of the row that holds this title. */
    private fun chooseFromTheMenu(item: String) {
        val optionsOnThisRow = hasContentDescription("Task options") and hasAnyAncestor(hasText(title))
        rule.waitUntil(5_000) { rule.onAllNodes(optionsOnThisRow).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(optionsOnThisRow).performClick()
        rule.onNodeWithText(item).performClick()
    }

    private fun awaitText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun stored(): Task? = runBlocking { app.container.store.get(taskId) }

    private fun tasksTitled(): List<Task> =
        runBlocking { app.container.store.observeTasks().first().filter { it.title == title } }
}
