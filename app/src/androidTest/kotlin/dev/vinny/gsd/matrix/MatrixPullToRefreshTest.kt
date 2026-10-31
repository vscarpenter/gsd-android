package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import dev.vinny.gsd.theme.GsdTheme
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

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun aPullWhenSignedInRequestsOneSync() {
        show(signedIn = true)
        pull()
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
