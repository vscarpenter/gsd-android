package dev.vinny.gsd.matrix

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PullRefreshStateTest {
    @Test
    fun `no pull shows no spinner`() {
        assertFalse(isPullRefreshing(pulledAtRun = null, finishedRuns = 3, signedIn = true))
    }

    @Test
    fun `a pull shows the spinner until a run finishes`() {
        assertTrue(isPullRefreshing(pulledAtRun = 3, finishedRuns = 3, signedIn = true))
        assertFalse(isPullRefreshing(pulledAtRun = 3, finishedRuns = 4, signedIn = true))
    }

    @Test
    fun `signing out clears the spinner, since the run it waits for never comes`() {
        assertFalse(isPullRefreshing(pulledAtRun = 3, finishedRuns = 3, signedIn = false))
    }
}
