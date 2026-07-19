package dev.vinny.gsd.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Analytics
import dev.vinny.gsd.model.DashboardStats
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.ZoneId

@Composable
fun DashboardScreen(container: AppContainer, onOpenOverdue: () -> Unit, modifier: Modifier = Modifier) {
    val tasks by container.store.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    var window by remember { mutableIntStateOf(7) }
    val stats = remember(tasks, window) {
        Analytics.compute(tasks, window, Instant.now(), ZoneId.systemDefault())
    }
    DashboardContent(
        stats = stats,
        window = window,
        onWindowChange = { window = it },
        onOpenOverdue = onOpenOverdue,
        isEmpty = tasks.isEmpty(),
        modifier = modifier,
    )
}

@Composable
fun DashboardContent(
    stats: DashboardStats,
    window: Int,
    onWindowChange: (Int) -> Unit,
    onOpenOverdue: () -> Unit,
    isEmpty: Boolean,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    if (isEmpty) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.dashboard_empty), style = GsdType.quadrantTitle, color = gsd.ink2)
        }
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (stats.overdueCount > 0) {
            Text(
                stringResource(R.string.dashboard_overdue_banner, stats.overdueCount),
                style = GsdType.subhead,
                color = gsd.alert,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(GsdShapes.input)
                    .background(gsd.alertWash)
                    .clickable(onClick = onOpenOverdue)
                    .padding(12.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.dashboard_active), "${stats.active}", Modifier.weight(1f))
            StatCard(stringResource(R.string.dashboard_completed), "${stats.completed}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                stringResource(R.string.dashboard_rate),
                "${(stats.completionRate * 100).toInt()}%",
                Modifier.weight(1f),
            )
            StatCard(
                stringResource(R.string.dashboard_tracked),
                TimeTracking.formatMinutes(stats.trackedTotalMinutes),
                Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(7, 30, 90).forEach { days ->
                FilterChip(
                    selected = window == days,
                    onClick = { onWindowChange(days) },
                    label = { Text("${days}d", style = GsdType.metadata) },
                )
            }
        }
        TrendChart(stats)
        QuadrantBars(
            title = stringResource(R.string.dashboard_distribution),
            values = stats.activeByQuadrant.mapValues { it.value.toFloat() },
            format = { "${it.toInt()}" },
        )
        if (stats.topTags.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.dashboard_top_tags).uppercase(), style = GsdType.caption, color = gsd.ink3)
                stats.topTags.forEach { (tag, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("#$tag", style = GsdType.metadata, color = gsd.ink2, modifier = Modifier.weight(1f))
                        Text("$count", style = GsdType.metadata.copy(fontFeatureSettings = "tnum"), color = gsd.ink2)
                    }
                }
            }
        }
        if (stats.minutesByQuadrant.values.any { it > 0 }) {
            QuadrantBars(
                title = stringResource(R.string.dashboard_time_by_quadrant),
                values = stats.minutesByQuadrant.mapValues { it.value.toFloat() },
                format = { TimeTracking.formatMinutes(it.toInt()) },
            )
        }
        if (stats.upcoming.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.dashboard_upcoming).uppercase(), style = GsdType.caption, color = gsd.ink3)
                stats.upcoming.forEach { task ->
                    Text(task.title, style = GsdType.subhead, color = gsd.ink)
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = modifier
            .clip(GsdShapes.input)
            .background(gsd.surface)
            .border(1.dp, gsd.hairline, GsdShapes.input)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value, style = GsdType.title, color = gsd.ink) // Newsreader numerals (spec §5.4)
        Text(label.uppercase(), style = GsdType.caption, color = gsd.ink3)
    }
}

/** Created vs completed per day — two lines on a quiet Canvas (spec §8.15). */
@Composable
private fun TrendChart(stats: DashboardStats) {
    val gsd = LocalGsdColors.current
    val maxValue = stats.trend.maxOf { maxOf(it.created, it.completed) }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.dashboard_trend).uppercase(), style = GsdType.caption, color = gsd.ink3)
        Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
            val stepX = size.width / (stats.trend.size - 1).coerceAtLeast(1)
            fun path(selector: (dev.vinny.gsd.model.TrendPoint) -> Int): Path {
                val path = Path()
                stats.trend.forEachIndexed { index, point ->
                    val x = index * stepX
                    val y = size.height * (1f - selector(point).toFloat() / maxValue)
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                return path
            }
            drawPath(path { it.created }, color = gsd.tide, style = Stroke(width = 3f))
            drawPath(path { it.completed }, color = gsd.success, style = Stroke(width = 3f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(gsd.tide, stringResource(R.string.dashboard_created))
            LegendDot(gsd.success, stringResource(R.string.dashboard_done))
        }
    }
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    val gsd = LocalGsdColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.height(8.dp).width(8.dp).clip(CircleShape).background(color))
        Text(label, style = GsdType.metadata, color = gsd.ink2)
    }
}

@Composable
private fun QuadrantBars(title: String, values: Map<Quadrant, Float>, format: (Float) -> String) {
    val gsd = LocalGsdColors.current
    val max = values.values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title.uppercase(), style = GsdType.caption, color = gsd.ink3)
        Quadrant.entries.forEach { quadrant ->
            val value = values[quadrant] ?: 0f
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(quadrant.title, style = GsdType.metadata, color = gsd.accent(quadrant),
                    modifier = Modifier.width(72.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(gsd.sunken),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value / max)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(gsd.accent(quadrant)),
                    )
                }
                Text(format(value), style = GsdType.metadata.copy(fontFeatureSettings = "tnum"), color = gsd.ink2)
            }
        }
    }
}

