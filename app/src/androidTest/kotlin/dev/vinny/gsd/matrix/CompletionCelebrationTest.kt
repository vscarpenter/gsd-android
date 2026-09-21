package dev.vinny.gsd.matrix

import android.animation.ValueAnimator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.browse.BrowseScreen
import dev.vinny.gsd.browse.SmartViewListScreen
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review finding 50: only Matrix celebrated a completion. Spec 8.4 says a completion always gets
 * the success haptic and the confetti. The four smart view list and Browse search tests failed
 * first, and they are the proof. The three Matrix tests and the no-confetti reopen test passed
 * before the fix. They are guards, and each one failed under a mutation of the line it guards.
 *
 * Review finding 51: a reopen got no haptic, against spec 5.8. Its test failed first.
 */
class CompletionCelebrationTest {
    @get:Rule
    val rule = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<GsdApp>()
    private val title = "celebrate me"
    private lateinit var taskId: String
    private val haptics = CountingHaptics()

    private class CountingHaptics : HapticFeedback {
        val performed = CopyOnWriteArrayList<HapticFeedbackType>()
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    /** The search field holds the same text on Browse, so a row is the match that takes no typing. */
    private val row = hasText(title) and !hasSetTextAction()

    /**
     * Urgent and important, so Matrix and Today's Focus both list it. These tests write through the
     * real store, so a signed-in device would sync their tasks to the backend.
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
    fun completingOnMatrixGivesTheSuccessHaptic() {
        show { MatrixScreen(container = app.container) }

        tapTheDisc("Mark complete")

        awaitCompleted(true)
        assertEquals(1, confirms())
    }

    @Test
    fun completingOnMatrixShowsTheConfetti() {
        assumeTrue("animators are off, so no confetti is drawn", ValueAnimator.areAnimatorsEnabled())
        show { MatrixScreen(container = app.container) }

        tapTheDisc("Mark complete")

        assertTrue(confettiAppears())
    }

    /** A Matrix swipe skips the menu builder and goes through the screen's own toggle. */
    @Test
    fun aSwipeCompleteOnMatrixGivesTheSuccessHaptic() {
        show { MatrixScreen(container = app.container) }
        rule.waitUntil(5_000) { rule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }

        rule.onNode(row).performTouchInput { swipeRight() }

        awaitCompleted(true)
        assertEquals(1, confirms())
    }

    @Test
    fun completingOnASmartViewListGivesTheSuccessHaptic() {
        show { SmartViewListScreen(app.container, "today-focus") }

        tapTheDisc("Mark complete")

        awaitCompleted(true)
        assertEquals(1, confirms())
    }

    @Test
    fun completingOnASmartViewListShowsTheConfetti() {
        assumeTrue("animators are off, so no confetti is drawn", ValueAnimator.areAnimatorsEnabled())
        show { SmartViewListScreen(app.container, "today-focus") }

        tapTheDisc("Mark complete")

        assertTrue(confettiAppears())
    }

    @Test
    fun completingInBrowseSearchResultsGivesTheSuccessHaptic() {
        showBrowseSearchResults()

        tapTheDisc("Mark complete")

        awaitCompleted(true)
        assertEquals(1, confirms())
    }

    @Test
    fun completingInBrowseSearchResultsShowsTheConfetti() {
        assumeTrue("animators are off, so no confetti is drawn", ValueAnimator.areAnimatorsEnabled())
        showBrowseSearchResults()

        tapTheDisc("Mark complete")

        assertTrue(confettiAppears())
    }

    /** Spec 5.8 gives the haptic to a completion and to a reopen. iOS does the same. */
    @Test
    fun reopeningATaskGivesTheSuccessHapticToo() {
        showTheTaskCompleted()

        tapTheDisc("Mark incomplete")

        awaitCompleted(false)
        assertEquals(1, confirms())
    }

    @Test
    fun reopeningATaskShowsNoConfetti() {
        showTheTaskCompleted()

        tapTheDisc("Mark incomplete")

        assertFalse(confettiAppears())
        awaitCompleted(false) // or the absence above could mean the tap never landed
    }

    private fun show(screen: @Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalHapticFeedback provides haptics) { GsdTheme { screen() } }
    }

    /** All Completed lists a finished task, and its disc reopens it. */
    private fun showTheTaskCompleted() {
        runBlocking { app.container.store.setCompleted(taskId, true) }
        show { SmartViewListScreen(app.container, "all-completed") }
    }

    private fun showBrowseSearchResults() {
        show {
            BrowseScreen(
                container = app.container,
                onOpenArchive = {}, onOpenTrash = {}, onOpenView = {}, onNewView = {}, onEditView = {},
            )
        }
        rule.onNodeWithTag("browse_search").performTextInput(title)
    }

    /** Other tests may leave tasks on screen, so tap the disc of the row that holds this title. */
    private fun tapTheDisc(label: String) {
        val discOnThisRow = hasContentDescription(label) and hasAnyAncestor(row)
        rule.waitUntil(5_000) { rule.onAllNodes(discOnThisRow).fetchSemanticsNodes().isNotEmpty() }
        // An idle wait on an advancing clock runs the 1.6 s confetti to its end, and the node goes with it.
        rule.mainClock.autoAdvance = false
        rule.onNode(discOnThisRow).performClick()
    }

    private fun awaitCompleted(completed: Boolean) =
        rule.waitUntil(5_000) { runBlocking { app.container.store.get(taskId) }?.completed == completed }

    /** Steps ten frames by hand. The overlay composes on the first frame after the tap. */
    private fun confettiAppears(): Boolean {
        repeat(10) {
            rule.mainClock.advanceTimeByFrame()
            if (rule.onAllNodesWithTag("confetti").fetchSemanticsNodes().isNotEmpty()) return true
        }
        return false
    }

    private fun confirms(): Int = haptics.performed.count { it == HapticFeedbackType.Confirm }
}
