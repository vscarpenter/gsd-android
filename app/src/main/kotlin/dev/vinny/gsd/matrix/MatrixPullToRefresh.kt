package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.vinny.gsd.theme.LocalGsdColors

/**
 * Whether a pull's spinner still shows. It compares a run count, not the `syncing` flag: that flag is a
 * conflated state, so a quick run can come and go between frames, and the spinner would never clear.
 */
internal fun isPullRefreshing(pulledAtRun: Long?, finishedRuns: Long, signedIn: Boolean): Boolean =
    signedIn && pulledAtRun == finishedRuns

/** Spec §6.1: a pull runs a manual sync when signed in. Signed out, there's nothing to sync, so no gesture. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MatrixPullToRefresh(
    signedIn: Boolean,
    finishedRuns: Long,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val state = rememberPullToRefreshState()
    var pulledAtRun by remember { mutableStateOf<Long?>(null) }
    val refreshing = isPullRefreshing(pulledAtRun, finishedRuns, signedIn)
    Box(
        modifier.pullToRefresh(
            isRefreshing = refreshing,
            state = state,
            enabled = signedIn,
            onRefresh = {
                pulledAtRun = finishedRuns
                onRefresh()
            },
        ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = gsd.surface,
            color = gsd.tint,
        )
    }
}
