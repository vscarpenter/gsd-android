package dev.vinny.gsd.matrix

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TodayView
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * "Today": up to three picks above the matrix (D-13, spec-top-three.md §4). It suggests and offers and
 * never picks; every write waits for a tap. With no picks it is one quiet line. Copy never says missed,
 * unfinished, or failed, and shows no progress meter.
 */
@Composable
fun TodaySection(
    view: TodayView,
    suggestionsOpen: Boolean,
    tidyDismissed: Boolean,
    today: LocalDate,
    onToggleSuggestions: () -> Unit,
    onAdd: (Task) -> Unit,
    onTidy: () -> Unit,
    onNotNow: () -> Unit,
    rowActions: (Task) -> TaskRowActions,
    modifier: Modifier = Modifier,
) {
    val titleCd = stringResource(R.string.today_title_cd, view.picks.size)
    val ordinals = listOf(
        stringResource(R.string.today_first),
        stringResource(R.string.today_second),
        stringResource(R.string.today_third),
    )
    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("today_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.today_title),
            style = GsdType.quadrantTitle,
            color = LocalGsdColors.current.ink,
            modifier = Modifier.semantics { heading(); contentDescription = titleCd },
        )
        view.picks.forEachIndexed { index, task ->
            val actions = rowActions(task)
            SwipeTaskRow(
                task = task,
                onComplete = actions.onToggleComplete,
                onDelete = actions.onDelete,
                onSnooze = { actions.onSnooze(QuickSnooze) },
                onMarkUrgent = actions.onMarkUrgent,
            ) {
                TaskRowWithMenu(
                    task = task,
                    badges = TaskBadges(0, 0),
                    actions = actions,
                    positionLabel = ordinals.getOrNull(index),
                )
            }
        }
        if (view.needsTidy && !tidyDismissed) TidyOffer(onTidy, onNotNow)
        view.carryOver?.let { offer ->
            OfferGroup(carryOverTitle(offer.from, today), offer.tasks, onAdd)
        }
        if (view.hasFreeSlot) {
            PickLine(suggestionsOpen, onToggleSuggestions)
            if (suggestionsOpen) {
                if (view.suggestions.isEmpty()) {
                    Text(
                        stringResource(R.string.today_nothing_to_suggest),
                        style = GsdType.metadata,
                        color = LocalGsdColors.current.ink2,
                        modifier = Modifier.testTag("today_nothing_to_suggest"),
                    )
                } else {
                    OfferGroup(null, view.suggestions, onAdd)
                }
            }
        }
    }
}

@Composable
private fun carryOverTitle(from: LocalDate, today: LocalDate): String =
    if (from == today.minusDays(1)) {
        stringResource(R.string.today_from_yesterday)
    } else {
        stringResource(R.string.today_from_weekday, weekdayName(from.dayOfWeek))
    }

private fun weekdayName(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

/** With no picks this is the whole section: one dashed line that opens the suggestions. */
@Composable
private fun PickLine(open: Boolean, onToggle: () -> Unit) {
    val gsd = LocalGsdColors.current
    val actionLabel = stringResource(if (open) R.string.today_hide_suggestions else R.string.today_show_suggestions)
    Text(
        stringResource(R.string.today_pick_line),
        style = GsdType.subhead,
        color = gsd.ink2,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .border(1.dp, gsd.hairlineStrong, GsdShapes.card)
            .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onToggle)
            .padding(14.dp)
            .testTag("today_pick_line"),
    )
}

@Composable
private fun OfferGroup(title: String?, tasks: List<Task>, onAdd: (Task) -> Unit) {
    val gsd = LocalGsdColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (title != null) {
            Text(
                title,
                style = GsdType.metadata.copy(fontFamily = GsdType.subhead.fontFamily),
                color = gsd.ink2,
                modifier = Modifier.semantics { heading() },
            )
        }
        tasks.forEach { task ->
            val addCd = stringResource(R.string.today_add_cd, task.title)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    task.title,
                    style = GsdType.subhead,
                    color = gsd.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onAdd(task) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics { contentDescription = addCd },
                ) {
                    Text(stringResource(R.string.today_add), style = GsdType.subhead, color = gsd.tint)
                }
            }
        }
    }
}

@Composable
private fun TidyOffer(onTidy: () -> Unit, onNotNow: () -> Unit) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = Modifier.fillMaxWidth().border(1.dp, gsd.hairline, GsdShapes.card).padding(14.dp).testTag("today_tidy"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.today_tidy_prompt), style = GsdType.subhead, color = gsd.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onTidy, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.today_tidy), style = GsdType.subhead, color = gsd.tint)
            }
            TextButton(onClick = onNotNow, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                Text(stringResource(R.string.today_not_now), style = GsdType.subhead, color = gsd.ink2)
            }
        }
    }
}
