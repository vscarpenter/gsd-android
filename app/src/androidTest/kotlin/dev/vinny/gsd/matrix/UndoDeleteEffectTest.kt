package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
import dev.vinny.gsd.theme.GsdTheme
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNull

/**
 * Found by the reviewer of #18. A tab switch keeps the view model and cancels the effect. The
 * cancelled effect never cleared the record, so the same Undo offer came back on the next visit.
 */
class UndoDeleteEffectTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var actions: TaskActionsViewModel
    private var hosted by mutableStateOf(true)

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, Clock.systemUTC())
        actions = TaskActionsViewModel(store, TrashStore(db, Clock.systemUTC()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun anUndoOfferEndsWhenItsScreenLeaves() {
        val task = add("leave me")
        showTheEffect()
        actions.delete(task.id)
        awaitText("Undo")

        hosted = false // what a tab switch does to the screen
        rule.waitForIdle()
        hosted = true
        rule.waitForIdle()

        rule.onAllNodesWithText("Undo").assertCountEquals(0)
        assertNull(actions.lastDeleted.value)
    }

    /** A second delete cancels the first effect too. That cancellation must not clear the new record. */
    @Test
    fun aSecondDeleteReplacesTheFirstOffer() {
        val first = add("first")
        val second = add("second")
        showTheEffect()
        actions.delete(first.id)
        awaitText("Deleted \"first\"")

        actions.delete(second.id)

        awaitText("Deleted \"second\"")
    }

    private fun add(title: String): Task = runBlocking {
        store.add(Task(title = title, urgent = true, important = true, createdAt = t0, updatedAt = t0))
    }

    private fun showTheEffect() = rule.setContent {
        GsdTheme {
            val host = remember { SnackbarHostState() }
            Box {
                SnackbarHost(host)
                if (hosted) UndoDeleteEffect(actions, host)
            }
        }
    }

    private fun awaitText(text: String) =
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
}
