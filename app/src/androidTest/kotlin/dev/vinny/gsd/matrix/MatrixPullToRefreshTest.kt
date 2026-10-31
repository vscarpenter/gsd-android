package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Spec §6.1. The list here stands in for `MatrixList`: the gesture reaches the box through nested scroll. */
class MatrixPullToRefreshTest {
    @get:Rule
    val rule = createComposeRule()

    private var refreshes = 0
    private var finishedRuns by mutableLongStateOf(0L)

    private fun show(signedIn: Boolean) {
        rule.setContent {
            GsdTheme {
                MatrixPullToRefresh(
                    signedIn = signedIn,
                    finishedRuns = finishedRuns,
                    onRefresh = { refreshes++ },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(50) { Text("Row $it") }
                    }
                }
            }
        }
    }

    private fun pull() {
        rule.onNodeWithTag("list").performTouchInput { swipeDown() }
        rule.waitForIdle()
    }

    /** One card in Do First (top left). Eliminate (bottom right) is empty. */
    private fun showGrid() {
        val stamp = Instant.parse("2026-07-19T12:00:00.000Z")
        val sections = buildSections(
            listOf(Task(title = "urgent thing", urgent = true, important = true, createdAt = stamp, updatedAt = stamp)),
        )
        rule.setContent {
            GsdTheme {
                MatrixPullToRefresh(
                    signedIn = true,
                    finishedRuns = finishedRuns,
                    onRefresh = { refreshes++ },
                    modifier = Modifier.fillMaxSize().testTag("board"),
                ) {
                    MatrixGrid(sections, onToggleComplete = {}, onOpen = {}, onDropToQuadrant = { _, _ -> })
                }
            }
        }
    }

    /** From a point given as fractions of the board, straight down to its bottom edge. */
    private fun pullBoardFrom(xFraction: Float, yFraction: Float) {
        rule.onNodeWithTag("board").performTouchInput {
            val start = Offset(width * xFraction, height * yFraction)
            swipe(start, Offset(start.x, height - 1f), durationMillis = 400)
        }
        rule.waitForIdle()
    }

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun aPullWhenSignedInRequestsOneSync() {
        show(signedIn = true)
        pull()
        assertEquals(1, refreshes)
    }

    @Test
    fun aPullFromAGridCardRequestsOneSync() {
        showGrid()
        pullBoardFrom(xFraction = 0.25f, yFraction = 0.12f)
        assertEquals(1, refreshes)
    }

    @Test
    fun aPullFromAnEmptyGridQuadrantRequestsOneSync() {
        showGrid()
        pullBoardFrom(xFraction = 0.75f, yFraction = 0.7f)
        assertEquals(1, refreshes)
    }

    @Test
    fun aPullWhenSignedOutDoesNothing() {
        show(signedIn = false)
        pull()
        assertEquals(0, refreshes)
        rule.onNode(spinner).assertDoesNotExist()
    }

    @Test
    fun theSpinnerHoldsUntilARunFinishes() {
        show(signedIn = true)
        pull()
        rule.onNode(spinner).assertExists()

        finishedRuns = 1
        rule.waitForIdle()

        rule.onNode(spinner).assertDoesNotExist()
    }
}
