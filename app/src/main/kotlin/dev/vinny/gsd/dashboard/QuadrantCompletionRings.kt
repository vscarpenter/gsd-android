package dev.vinny.gsd.dashboard

import android.animation.ValueAnimator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Analytics
import dev.vinny.gsd.model.QuadrantStat
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

private val ringSize = 196.dp
private val ringWidth = 13.dp
private val ringGap = 5.dp

/** Four concentric completion rings, Q1 outermost, over their wash tracks (S§2.2). */
@Composable
fun QuadrantCompletionRings(stats: List<QuadrantStat>, overallRate: Float, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val animate = remember { ValueAnimator.areAnimatorsEnabled() }
    val growth = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) growth.animateTo(1f, tween(900, easing = EaseOut)) }
    val overall = Analytics.percent(overallRate)
    val ringsLabel = stringResource(R.string.dashboard_rings_a11y, overall)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(ringSize).align(Alignment.CenterHorizontally)
                .clearAndSetSemantics { contentDescription = ringsLabel },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = ringWidth.toPx(); val step = (ringWidth + ringGap).toPx() * 2
                stats.forEachIndexed { index, stat ->
                    val diameter = size.width - index * step
                    val inset = (size.width - diameter) / 2 + stroke / 2
                    val arcSize = Size(diameter - stroke, diameter - stroke)
                    val topLeft = Offset(inset, inset)
                    drawArc(gsd.wash(stat.quadrant), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(gsd.accent(stat.quadrant), -90f, 360f * stat.completionRate * growth.value, false, topLeft, arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(67.dp)) {
                Text(overall, style = GsdType.title.copy(fontFeatureSettings = "tnum"), color = gsd.ink, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 28.sp))
                Text(stringResource(R.string.dashboard_rings_complete), style = GsdType.caption, color = gsd.ink3)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            stats.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { LegendCell(it, Modifier.weight(1f)) } }
            }
        }
    }
}

/** "50%", or an em dash for a quadrant with no tasks yet (S§2.2). Pure — JVM-testable. */
internal fun legendValue(stat: QuadrantStat): String =
    if (stat.total == 0) "—" else Analytics.percent(stat.completionRate)

@Composable
private fun LegendCell(stat: QuadrantStat, modifier: Modifier) {
    val gsd = LocalGsdColors.current
    val value = legendValue(stat)
    val spoken = if (stat.total == 0) stringResource(R.string.dashboard_rings_legend_none, stat.quadrant.title)
        else stringResource(R.string.dashboard_rings_legend, stat.quadrant.title, Analytics.percent(stat.completionRate))
    Row(
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(gsd.accent(stat.quadrant)))
        Text(stat.quadrant.title, style = GsdType.subhead, color = gsd.ink2, modifier = Modifier.weight(1f))
        Text(value, style = GsdType.subhead.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"), color = gsd.ink)
    }
}
