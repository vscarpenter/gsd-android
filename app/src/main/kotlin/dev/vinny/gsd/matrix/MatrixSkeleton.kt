package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.SkeletonBlock
import dev.vinny.gsd.ui.loadingSemantics
import dev.vinny.gsd.ui.skeletonShimmer

private val cardCounts = listOf(3, 2, 2, 1) // the web's deliberately uneven silhouette (S§4.3)

/** First-frame silhouette of the matrix: Q1→Q4 sections, stacked (compact) or 2×2 (expanded). */
@Composable
fun MatrixSkeleton(expanded: Boolean = false, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.skeleton_loading)
    val sections: @Composable (Quadrant, Int) -> Unit = { quadrant, cards ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBlock(height = 26.dp, width = 26.dp, shape = GsdShapes.tile)
                SkeletonBlock(height = 16.dp, width = 120.dp, shape = RoundedCornerShape(4.dp), modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                SkeletonBlock(height = 12.dp, width = 24.dp, shape = RoundedCornerShape(4.dp))
            }
            repeat(cards) { CardSilhouette(quadrant) }
        }
    }
    if (expanded) {
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp).loadingSemantics(label).skeletonShimmer(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf(
                listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE),
                listOf(Quadrant.DELEGATE, Quadrant.ELIMINATE),
            ).forEach { rowQuadrants ->
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    rowQuadrants.forEach { quadrant ->
                        val index = Quadrant.entries.indexOf(quadrant)
                        Column(modifier = Modifier.weight(1f)) { sections(quadrant, cardCounts[index]) }
                    }
                }
            }
        }
    } else {
        val body: @Composable ColumnScope.() -> Unit = {
            Quadrant.entries.forEachIndexed { i, q -> sections(q, cardCounts[i]) }
        }
        Column(
            modifier = modifier.fillMaxSize().padding(20.dp).loadingSemantics(label).skeletonShimmer(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = body,
        )
    }
}

@Composable
private fun CardSilhouette(quadrant: Quadrant) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clip(GsdShapes.card).background(gsd.surface).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(3.dp).height(36.dp).clip(CircleShape).background(gsd.accent(quadrant)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SkeletonBlock(height = 14.dp, width = 180.dp, shape = RoundedCornerShape(4.dp))
            SkeletonBlock(height = 10.dp, width = 96.dp, shape = RoundedCornerShape(4.dp))
        }
    }
}
