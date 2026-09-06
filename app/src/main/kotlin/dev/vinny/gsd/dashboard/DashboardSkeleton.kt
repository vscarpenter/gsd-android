package dev.vinny.gsd.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.ui.SkeletonBlock
import dev.vinny.gsd.ui.loadingSemantics
import dev.vinny.gsd.ui.skeletonShimmer

/** First-frame silhouette of the dashboard: stat tiles, then trend, then distribution (S§4.2). */
@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.skeleton_loading)
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp).loadingSemantics(label).skeletonShimmer(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                SkeletonBlock(height = 86.dp, shape = GsdShapes.input, modifier = Modifier.weight(1f))
                SkeletonBlock(height = 86.dp, shape = GsdShapes.input, modifier = Modifier.weight(1f))
            }
        }
        SkeletonBlock(height = 270.dp, shape = GsdShapes.card)
        SkeletonBlock(height = 320.dp, shape = GsdShapes.card)
    }
}
