package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag as semanticsTestTag
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Escalation
import dev.vinny.gsd.model.EscalationResult
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.ZoneId

/**
 * The "Escalate tasks due within" window in days, null when Off. Provided once at the root so every
 * screen that draws a task row agrees. The default is Off, which keeps a bare preview or test quiet.
 */
val LocalEscalationWindow = compositionLocalOf<Int?> { null }

/** The escalation for [task] under the current window, or null when it isn't escalating. */
@Composable
internal fun rememberEscalation(task: Task): EscalationResult? {
    val window = LocalEscalationWindow.current
    return Escalation.evaluate(task, Instant.now(), window, ZoneId.systemDefault()).takeIf { it.escalating }
}

/** The due chip's phrase (spec-escalation.md §5): "due today", "due in 2 days", "3 days overdue". */
@Composable
internal fun escalationDuePhrase(escalation: EscalationResult): String {
    val days = escalation.daysUntilDue ?: 0
    return when {
        days < 0 -> pluralStringResource(R.plurals.escalation_overdue_days, -days, -days)
        days == 0 -> stringResource(R.string.escalation_due_today)
        days == 1 -> stringResource(R.string.escalation_due_tomorrow)
        else -> pluralStringResource(R.plurals.escalation_due_in_days, days, days)
    }
}

/** "Moves to Do First" or "Moves to Delegate". */
@Composable
internal fun escalationMovesTo(escalation: EscalationResult): String =
    stringResource(R.string.escalation_moves_to, escalation.target?.quadrant?.title.orEmpty())

/**
 * The card marker: text and an up arrow in the target quadrant's ink, never color alone. One
 * description reads it as "Escalating: due tomorrow, not marked urgent."
 */
@Composable
internal fun EscalationTag(escalation: EscalationResult, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val target = escalation.target?.quadrant ?: return
    val description = stringResource(R.string.escalation_cd, escalationDuePhrase(escalation))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(CircleShape)
            .background(gsd.wash(target))
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clearAndSetSemantics {
                contentDescription = description
                semanticsTestTag = "escalation_tag"
            },
    ) {
        Icon(Icons.Outlined.ArrowUpward, contentDescription = null, tint = gsd.accentInk(target), modifier = Modifier.size(14.dp))
        Text(stringResource(R.string.escalation_tag), style = GsdType.metadata, color = gsd.accentInk(target))
    }
}
