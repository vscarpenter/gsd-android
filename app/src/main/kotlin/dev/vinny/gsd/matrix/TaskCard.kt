package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Task card per spec §5.6: accent spine, content column, overflow + completion disc. */
@Composable
fun TaskCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    val accent = gsd.accent(task.quadrant)
    Surface(
        onClick = onClick,
        shape = GsdShapes.card,
        color = gsd.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, gsd.hairline, GsdShapes.card),
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
                MetadataRow(task)
                SubtaskProgress(task)
            }
            IconButton(onClick = {}) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = stringResource(R.string.cd_task_options),
                    tint = gsd.ink3,
                )
            }
            CompletionDisc(task, onToggleComplete)
        }
    }
}

@Composable
private fun MetadataRow(task: Task) {
    val gsd = LocalGsdColors.current
    val due = task.dueDate ?: return
    val (label, color) = dueLabel(due, Instant.now(), gsd.tide, gsd.alert, gsd.ink3)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        val overdue = color == gsd.alert
        Icon(
            if (overdue) Icons.Outlined.Warning else Icons.Outlined.Event,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Text(label, style = GsdType.metadata, color = color, maxLines = 1)
        if (task.recurrence != dev.vinny.gsd.model.Recurrence.NONE) {
            Icon(Icons.Outlined.Repeat, contentDescription = null, tint = gsd.ink3, modifier = Modifier.size(14.dp))
        }
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
