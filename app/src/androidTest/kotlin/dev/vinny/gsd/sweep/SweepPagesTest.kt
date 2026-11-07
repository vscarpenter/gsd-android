package dev.vinny.gsd.sweep

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.vinny.gsd.model.SweepReason
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** The sweep's copy and shape (spec-weekly-sweep.md §7): position only, facts only, and the end. */
class SweepPagesTest {
    @get:Rule
    val rule = createComposeRule()

    private val t0 = Instant.parse("2026-10-01T00:00:00Z")
    private val task = Task(id = "task-sweep-1", title = "Renew passport", urgent = false, important = true, createdAt = t0, updatedAt = t0)

    private fun showCard(reason: SweepReason, position: Int = 3, total: Int = 12, onDone: () -> Unit = {}) = rule.setContent {
        GsdTheme {
            SweepCardPage(
                card = SweepState.Card(task, reason, position, total),
                onAction = {}, onReturn = { _, _, _, _ -> }, onSetDue = { _, _ -> }, onMove = {}, onDone = onDone,
            )
        }
    }

    @Test
    fun theCardShowsPositionOnlyAndItsReason() {
        showCard(SweepReason.OVERDUE)
        rule.onNodeWithText("3 of 12").assertIsDisplayed()
        rule.onNodeWithText("Due date passed").assertIsDisplayed()
        rule.onNodeWithText("Renew passport").assertIsDisplayed()
    }

    @Test
    fun theStaleCardOffersItsActionsAndSkip() {
        showCard(SweepReason.STALE)
        listOf("Complete", "Move to quadrant", "New date", "Save for later", "Delete", "Skip").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun doneIsAlwaysThere() {
        var done = 0
        showCard(SweepReason.UNDATED_SCHEDULE, onDone = { done++ })
        rule.onNodeWithTag("sweep_done").performClick()
        assertEquals(1, done)
    }

    @Test
    fun theCopyNeverCountsWhatsLeftOrShames() {
        showCard(SweepReason.STALE)
        // Only the position line carries digits of a count
        listOf("behind", "neglected", "still", "missed", "forgot", "%", "left").forEach { word ->
            rule.onAllNodes(hasText(word, substring = true, ignoreCase = true)).assertCountEquals(0)
        }
    }

    private fun showEnd(offer: Boolean, onPick: () -> Unit = {}, onBack: () -> Unit = {}) = rule.setContent {
        GsdTheme { SweepEnd(offerTopThree = offer, onPick = onPick, onBack = onBack) }
    }

    @Test
    fun theEndOffersTopThreeWhileTodayHasAFreeSlot() {
        var picked = 0
        showEnd(offer = true, onPick = { picked++ })
        rule.onNodeWithText("That's the sweep.").assertIsDisplayed()
        rule.onNodeWithText("Pick today's top three").performClick()
        assertEquals(1, picked)
        rule.onNodeWithText("Back to matrix").assertIsDisplayed()
    }

    @Test
    fun aFullDayEndsOnThatsTheSweepAlone() {
        showEnd(offer = false)
        rule.onNodeWithText("That's the sweep.").assertIsDisplayed()
        rule.onAllNodes(hasText("Pick today's top three")).assertCountEquals(0)
    }

    @Test
    fun theEndShowsNoTally() {
        showEnd(offer = true)
        rule.onAllNodes(hasText("of ", substring = true)).assertCountEquals(0)
    }
}
