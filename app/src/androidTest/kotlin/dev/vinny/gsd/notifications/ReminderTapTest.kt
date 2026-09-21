package dev.vinny.gsd.notifications

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * Review finding 47: a reminder tap only asked the Matrix screen to open the task. From any other
 * tab nothing happened until the user went to Matrix. Spec 11.2 says the tap is gsd://task/<id>.
 */
class ReminderTapTest {
    // The v2 rule queues effects as production does.
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private lateinit var taskId: String

    /** These tests write through the real store, so a signed-in device would sync their tasks to the backend. */
    @Before
    fun seedOneTask() = runBlocking {
        assumeFalse("signed in: refusing to write test tasks", app.container.syncCoordinator.signedIn.value)
        app.container.prefs.setHasOnboarded(true)
        val now = Instant.now()
        taskId = app.container.store.add(
            Task(title = "tap my reminder", urgent = true, important = true, createdAt = now, updatedAt = now),
        ).id
    }

    @After
    fun removeTheTask() = runBlocking {
        app.container.store.delete(taskId)
        app.container.trashStore.deleteForever(taskId)
    }

    @Test
    fun aReminderTapOpensTheTaskFromAnotherTab() {
        ActivityScenario.launch(MainActivity::class.java).use {
            awaitSelected("Matrix")
            rule.onNode(navItem("Settings")).performClick()
            awaitSelected("Settings")

            app.startActivity(ReminderReceiver.tapIntent(app, taskId))

            rule.waitUntil(5_000) { rule.onAllNodesWithText("Edit Task").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    private fun awaitSelected(label: String) =
        rule.waitUntil(5_000) { rule.onAllNodes(navItem(label) and isSelected()).fetchSemanticsNodes().isNotEmpty() }

    /** The top bar repeats the screen name, so match the navigation item itself. */
    private fun navItem(label: String) = hasText(label) and isSelectable()
}
