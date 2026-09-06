package dev.vinny.gsd.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.SkeletonBlock
import dev.vinny.gsd.ui.loadingSemantics
import dev.vinny.gsd.ui.skeletonShimmer

/** First-frame silhouette of a smart-view row list (S§4.2). */
@Composable
fun TaskListSkeleton(rows: Int = 6, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val label = stringResource(R.string.skeleton_loading)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp)
            .clip(GsdShapes.small).background(gsd.surface).loadingSemantics(label).skeletonShimmer(),
    ) {
        repeat(rows) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(gsd.sunken))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    SkeletonBlock(height = 14.dp, width = if (index % 2 == 0) 210.dp else 150.dp, shape = RoundedCornerShape(4.dp))
                    SkeletonBlock(height = 10.dp, width = 96.dp, shape = RoundedCornerShape(4.dp))
                }
            }
            if (index < rows - 1) HorizontalDivider(color = gsd.hairline, modifier = Modifier.padding(start = 50.dp))
        }
    }
}
