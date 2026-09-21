package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowCircleRight
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Task card per spec §5.6: accent spine, content column, overflow + completion disc. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: Task,
    onToggleComplete: (() -> Unit)?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badges: TaskBadges = TaskBadges(0, 0),
    onOpenMenu: (() -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    val accent = gsd.accent(task.quadrant)
    Surface(
        shape = GsdShapes.card,
        color = gsd.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, gsd.hairline, GsdShapes.card)
            .combinedClickable(onClick = onClick, onLongClick = onOpenMenu),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    task.title,
                    style = GsdType.cardTitle,
                    color = if (task.completed) gsd.ink3 else gsd.ink,
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.description.isNotEmpty()) {
                    Text(
                        task.description,
                        style = GsdType.subhead,
                        color = if (task.completed) gsd.ink3 else gsd.ink2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (task.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        task.tags.take(4).forEach { tag ->
                            Text(
                                "#$tag",
                                style = GsdType.metadata,
                                color = accent,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(gsd.wash(task.quadrant))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                MetadataRow(task, badges)
                SubtaskProgress(task)
            }
            // A card with no menu shows no button. A long press and the button open the same menu (§8.1).
            if (onOpenMenu != null) {
                IconButton(onClick = onOpenMenu) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.cd_task_options),
                        tint = gsd.ink3,
                    )
                }
            }
            // Trash and Archive cannot complete a task, so they pass no callback and get no disc.
            if (onToggleComplete != null) CompletionDisc(task, onToggleComplete)
        }
    }
}

@Composable
private fun MetadataRow(task: Task, badges: TaskBadges) {
    val gsd = LocalGsdColors.current
    val running = TimeTracking.runningEntry(task)
    val snoozed = task.snoozedUntil?.takeIf { it.isAfter(Instant.now()) }
    val hasAny = task.dueDate != null || task.recurrence != dev.vinny.gsd.model.Recurrence.NONE ||
        badges.blocked > 0 || badges.blocking > 0 || running != null || task.timeSpent > 0 || snoozed != null
    if (!hasAny) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        task.dueDate?.let { due ->
            val (label, color) = dueLabel(due, Instant.now(), gsd.tide, gsd.alert, gsd.ink3)
            val overdue = color == gsd.alert
            Icon(
                if (overdue) Icons.Outlined.Warning else Icons.Outlined.Event,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Text(label, style = GsdType.metadata, color = color, maxLines = 1)
        }
        if (task.recurrence != dev.vinny.gsd.model.Recurrence.NONE) {
            Icon(Icons.Outlined.Repeat, contentDescription = null, tint = gsd.ink3, modifier = Modifier.size(14.dp))
        }
        if (badges.blocked > 0) {
            val cd = stringResource(R.string.cd_blocked_by, badges.blocked)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = gsd.ink2, modifier = Modifier.size(14.dp))
                Text("${badges.blocked}", style = GsdType.metadata, color = gsd.ink2)
            }
        }
        if (badges.blocking > 0) {
            val cd = stringResource(R.string.cd_blocking, badges.blocking)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics { contentDescription = cd },
            ) {
                Icon(
                    Icons.Outlined.ArrowCircleRight,
                    contentDescription = null,
                    tint = gsd.ink3,
                    modifier = Modifier.size(14.dp),
                )
                Text("${badges.blocking}", style = GsdType.metadata, color = gsd.ink3)
            }
        }
        when {
            running != null -> LiveTimerText(running.startedAt)
            task.timeSpent > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = gsd.ink3, modifier = Modifier.size(14.dp))
                Text(TimeTracking.formatMinutes(task.timeSpent), style = GsdType.metadata, color = gsd.ink3)
            }
            else -> Unit
        }
        snoozed?.let { until ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Bedtime, contentDescription = null, tint = gsd.ink3, modifier = Modifier.size(14.dp))
                Text(
                    TimeTracking.formatMinutes(
                        java.time.Duration.between(Instant.now(), until).toMinutes().toInt().coerceAtLeast(1),
                    ),
                    style = GsdType.metadata,
                    color = gsd.ink3,
                )
            }
        }
    }
}

/** Live `HH:MM:SS` for a running timer; the pulsing dot goes static under reduced motion (spec §5.8). */
@Composable
private fun LiveTimerText(startedAt: Instant) {
    val gsd = LocalGsdColors.current
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(startedAt) {
        while (true) {
            delay(1_000)
            now = Instant.now()
        }
    }
    val dotAlpha = if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
        val transition = rememberInfiniteTransition(label = "timer-dot")
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(animation = tween(850), repeatMode = RepeatMode.Reverse),
            label = "timer-dot-alpha",
        ).value
    } else {
        1f
    }
    val seconds = java.time.Duration.between(startedAt, now).seconds.coerceAtLeast(0)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(gsd.tint.copy(alpha = dotAlpha)),
        )
        Text(
            "%02d:%02d:%02d".format(seconds / 3600, (seconds % 3600) / 60, seconds % 60),
            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
            color = gsd.ink2,
        )
    }
}

/** Relative due formatting (§8.1): today in Tide, overdue in alert, else quiet ink. */
internal fun dueLabel(
    due: Instant,
    now: Instant,
    tide: androidx.compose.ui.graphics.Color,
    alert: androidx.compose.ui.graphics.Color,
    ink3: androidx.compose.ui.graphics.Color,
): Pair<String, androidx.compose.ui.graphics.Color> {
    val zone = ZoneId.systemDefault()
    val dueDay = due.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, dueDay)
    return when {
        days < 0L -> "Overdue" to alert
        days == 0L -> "Due today" to tide
        days == 1L -> "Due tomorrow" to ink3
        else -> "in $days days" to ink3
    }
}

@Composable
private fun SubtaskProgress(task: Task) {
    if (task.subtasks.isEmpty()) return
    val gsd = LocalGsdColors.current
    val done = task.subtasks.count { it.completed }
    val fraction = done.toFloat() / task.subtasks.size
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(gsd.sunken),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width((84 * fraction).dp)
                    .clip(CircleShape)
                    .background(if (fraction >= 1f) gsd.success else gsd.accent(task.quadrant)),
            )
        }
        Text("$done/${task.subtasks.size}", style = GsdType.metadata, color = gsd.ink2)
    }
}

@Composable
private fun CompletionDisc(task: Task, onToggleComplete: () -> Unit) {
    val gsd = LocalGsdColors.current
    val accent = gsd.accent(task.quadrant)
    val description = stringResource(
        if (task.completed) R.string.cd_mark_incomplete else R.string.cd_mark_complete,
    )
    IconButton(
        onClick = onToggleComplete,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = description },
    ) {
        if (task.completed) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = gsd.inkOnAccent,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(2.dp, gsd.ink3, CircleShape),
            )
        }
    }
}
