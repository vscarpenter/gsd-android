package dev.vinny.gsd.matrix

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
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
 * Guards the path from a swipe to the Undo snackbar (review finding 3). It is not the proof of that
 * fix: it passed on the old code in eight runs out of eight, at both swipe speeds. The proof is the
 * call count in SwipeTaskRowTest. This test fails when the swipe stops reaching the delete.
 */
class SwipeDeleteUndoTest {
    // The v2 rule queues effects as production does.
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private lateinit var taskId: String

    /** These tests write through the real store, so a signed-in device would sync their tasks to the backend. */
    @Before
    fun seedOneTaskInDoFirst() = runBlocking {
        assumeFalse("signed in: refusing to write test tasks", app.container.syncCoordinator.signedIn.value)
        app.container.prefs.setHasOnboarded(true)
        val now = Instant.now()
        taskId = app.container.store.add(
            Task(title = "swipe me away", urgent = true, important = true, createdAt = now, updatedAt = now),
        ).id
    }

    @After
    fun removeTheTask() = runBlocking {
        app.container.store.delete(taskId)
        app.container.trashStore.deleteForever(taskId)
    }

    @Test
    fun aSwipeDeleteOffersUndoAndUndoBringsTheTaskBack() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitText("swipe me away")

            rule.onNodeWithText("swipe me away").performTouchInput { swipeLeft() }
            awaitText("Undo")
            // The card must be gone first, or the last wait could pass on the card it started with.
            rule.waitUntil(5_000) { rule.onAllNodesWithText("swipe me away").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithText("Undo").performClick()

            awaitText("swipe me away")
        }
    }

    private fun awaitText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
