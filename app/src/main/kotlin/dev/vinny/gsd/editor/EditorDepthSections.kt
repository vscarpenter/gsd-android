package dev.vinny.gsd.editor

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.DuePreset
import dev.vinny.gsd.model.DueTime
import dev.vinny.gsd.model.NanoId
import dev.vinny.gsd.model.Recurrence
import dev.vinny.gsd.model.ReminderLabel
import dev.vinny.gsd.model.ReminderOptions
import dev.vinny.gsd.model.Subtask
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.HintedField
import dev.vinny.gsd.ui.formatTimeOfDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Due date (spec §8.3, §8.10): a date row that opens the Material date picker, a time row that opens
 * the time picker, and the four presets. Every due date used to land at local midnight, because the
 * presets were the only way in (review finding 6). A date with no time is "All day".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DueDateSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val due = draft.dueDate
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TappableValue(
            text = due?.let { DateTimeFormatter.ofPattern("EEE, MMM d").format(it.atZone(zone)) }
                ?: stringResource(R.string.due_pick_date),
            tag = "due_date_row",
            onClick = { pickingDate = true },
        )
        if (due != null) {
            TappableValue(
                text = DueTime.timeOf(due, zone)?.let { formatTimeOfDay(context, it) }
                    ?: stringResource(R.string.due_all_day),
                tag = "due_time_row",
                onClick = { pickingTime = true },
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                DuePreset.NONE to R.string.due_none,
                DuePreset.TODAY to R.string.due_today,
                DuePreset.THIS_WEEK to R.string.due_this_week,
                DuePreset.NEXT_WEEK to R.string.due_next_week,
            ).forEach { (preset, labelRes) ->
                FilterChip(
                    selected = preset.isSelectedFor(due, today, zone),
                    onClick = {
                        val resolved = preset.resolve(today)?.atStartOfDay(zone)?.toInstant()
                        onEdit { it.copy(dueDate = resolved) }
                    },
                    label = { Text(stringResource(labelRes), style = GsdType.metadata) },
                )
            }
        }
    }
    if (pickingDate) {
        DueDatePickerDialog(
            initial = due?.atZone(zone)?.toLocalDate() ?: today,
            onPick = { date -> onEdit { it.copy(dueDate = DueTime.onDate(it.dueDate, date, zone)) } },
            onDismiss = { pickingDate = false },
        )
    }
    if (pickingTime && due != null) {
        DueTimePickerDialog(
            initial = DueTime.timeOf(due, zone),
            onPick = { time ->
                onEdit { task -> task.dueDate?.let { task.copy(dueDate = DueTime.atTime(it, time, zone)) } ?: task }
            },
            onDismiss = { pickingTime = false },
        )
    }
}

/** A value the user taps to change, in the interactive tint and at least 48 dp tall. */
@Composable
private fun TappableValue(text: String, tag: String, onClick: () -> Unit) {
    Text(
        text,
        style = GsdType.body,
        color = LocalGsdColors.current.tint,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically)
            .testTag(tag),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // The picker counts in UTC days, so a local date travels as that day's UTC midnight
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text(stringResource(R.string.dialog_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueTimePickerDialog(initial: LocalTime?, onPick: (LocalTime?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val start = initial ?: LocalTime.of(9, 0) // a morning default for a date that had no time
    val state = rememberTimePickerState(
        initialHour = start.hour,
        initialMinute = start.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.due_time_title)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); onDismiss() }) {
                Text(stringResource(R.string.dialog_done))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(null); onDismiss() }) { Text(stringResource(R.string.due_all_day)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
            }
        },
    )
}

/** The words for a reminder offset, shared by the editor and Settings' default reminder. */
@Composable
internal fun reminderLabel(minutes: Int?): String = when (val label = minutes?.let(ReminderLabel::of)) {
    null -> stringResource(R.string.reminder_default)
    ReminderLabel.AtTime -> stringResource(R.string.reminder_at_time)
    is ReminderLabel.Minutes -> pluralStringResource(R.plurals.reminder_minutes_before, label.count, label.count)
    is ReminderLabel.Hours -> pluralStringResource(R.plurals.reminder_hours_before, label.count, label.count)
    is ReminderLabel.Days -> pluralStringResource(R.plurals.reminder_days_before, label.count, label.count)
}

@Composable
internal fun ReminderSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var open by remember { mutableStateOf(false) }
    val options: List<Int?> = listOf(null) + ReminderOptions.including(draft.notifyBefore)
    Box {
        Text(
            reminderLabel(draft.notifyBefore), style = GsdType.body, color = gsd.tint,
            modifier = Modifier.clickable(role = Role.Button) { open = true }.testTag("reminder_row").padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(reminderLabel(minutes), style = GsdType.body) },
                    onClick = { open = false; onEdit { it.copy(notifyBefore = minutes) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RepeatSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    // Wraps: at font scale 2.0 "Monthly" was squeezed into a one-letter column (finding 12)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            Recurrence.NONE to R.string.repeat_never,
            Recurrence.DAILY to R.string.repeat_daily,
            Recurrence.WEEKLY to R.string.repeat_weekly,
            Recurrence.MONTHLY to R.string.repeat_monthly,
        ).forEach { (recurrence, labelRes) ->
            FilterChip(
                selected = draft.recurrence == recurrence,
                onClick = { onEdit { it.copy(recurrence = recurrence) } },
                label = { Text(stringResource(labelRes), style = GsdType.metadata) },
            )
        }
    }
}

/** The §7.2 limits the editor enforces as the user types, so no field fails only at Save (finding 30). */
internal const val MAX_TAGS = 20
internal const val MAX_TAG_LENGTH = 30
private const val MAX_SUBTASKS = 50
private const val MAX_SUBTASK_LENGTH = 100
private const val MAX_ESTIMATE_DIGITS = 5 // 10,080 minutes, one week

/** The editor's field look: a `sunken` rounded box at least 48dp tall (review finding 30). */
internal fun Modifier.editorField(fill: Color): Modifier = this
    .fillMaxWidth()
    .heightIn(min = 48.dp)
    .clip(GsdShapes.input)
    .background(fill)
    .padding(horizontal = 12.dp)

@Composable
internal fun SubtasksSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var pending by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        draft.subtasks.forEach { subtask ->
            // The row is one checkbox with its state; a borderless disc had neither (finding 30)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = subtask.completed, role = Role.Checkbox) {
                        onEdit { task ->
                            task.copy(subtasks = task.subtasks.map {
                                if (it.id == subtask.id) it.copy(completed = !it.completed) else it
                            })
                        }
                    },
            ) {
                Checkbox(
                    checked = subtask.completed,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(
                        checkedColor = gsd.success,
                        uncheckedColor = gsd.ink3,
                        checkmarkColor = gsd.inkOnAccent,
                    ),
                )
                Text(
                    subtask.title,
                    style = GsdType.body,
                    color = if (subtask.completed) gsd.ink3 else gsd.ink,
                    textDecoration = if (subtask.completed) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    onEdit { task -> task.copy(subtasks = task.subtasks.filter { it.id != subtask.id }) }
                }) {
                    Icon(Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_delete_subtask, subtask.title),
                        tint = gsd.ink3, modifier = Modifier.size(14.dp))
                }
            }
        }
        if (draft.subtasks.size >= MAX_SUBTASKS) {
            Text(stringResource(R.string.subtasks_at_limit), style = GsdType.metadata, color = gsd.ink3)
        } else {
            Box(modifier = Modifier.editorField(gsd.sunken), contentAlignment = Alignment.CenterStart) {
                val hint = stringResource(R.string.subtask_add_hint)
                BasicTextField(
                    value = pending,
                    onValueChange = { new ->
                        if (new.endsWith(",") || new.endsWith("\n")) {
                            val title = new.dropLast(1).trim()
                            if (title.isNotEmpty()) {
                                onEdit { it.copy(subtasks = it.subtasks + Subtask(NanoId.generate(), title)) }
                            }
                            pending = ""
                        } else {
                            pending = new.take(MAX_SUBTASK_LENGTH)
                        }
                    },
                    textStyle = GsdType.subhead.copy(color = gsd.ink),
                    cursorBrush = gsd.cursor,
                    modifier = Modifier.fillMaxWidth().testTag("subtask_input"),
                    decorationBox = { inner -> HintedField(hint, pending.isEmpty(), GsdType.subhead, inner) },
                )
            }
        }
    }
}

@Composable
internal fun EstimateSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // A number keyboard and a unit; it was a bare "90" under a hint that printed the raw limit (finding 30)
        Row(
            modifier = Modifier.editorField(gsd.sunken),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val hint = stringResource(R.string.estimate_hint)
            BasicTextField(
                value = draft.estimatedMinutes?.toString().orEmpty(),
                onValueChange = { raw ->
                    val minutes = raw.filter(Char::isDigit).take(MAX_ESTIMATE_DIGITS).toIntOrNull()
                    onEdit { it.copy(estimatedMinutes = minutes) }
                },
                textStyle = GsdType.body.copy(color = gsd.ink),
                cursorBrush = gsd.cursor,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f).testTag("estimate_input"),
                decorationBox = { inner ->
                    HintedField(hint, draft.estimatedMinutes == null, GsdType.subhead, inner)
                },
            )
            draft.estimatedMinutes?.let { minutes ->
                Text(stringResource(R.string.estimate_unit), style = GsdType.body, color = gsd.ink2)
                if (minutes >= 60) {
                    Text("· ${TimeTracking.formatMinutes(minutes)}", style = GsdType.metadata, color = gsd.ink3)
                }
            }
        }
        val estimate = draft.estimatedMinutes
        if (estimate != null && draft.timeSpent > 0) {
            val over = draft.timeSpent > estimate
            Text(
                stringResource(
                    R.string.tracked_of,
                    TimeTracking.formatMinutes(draft.timeSpent),
                    TimeTracking.formatMinutes(estimate),
                ),
                style = GsdType.metadata,
                color = if (over) gsd.alert else gsd.ink2,
                modifier = if (over) Modifier.testTag("estimate_over") else Modifier,
            )
        }
    }
}

internal val snoozePresets: List<Pair<Duration, Int>> = listOf(
    Duration.ofMinutes(15) to R.string.snooze_15m,
    Duration.ofMinutes(30) to R.string.snooze_30m,
    Duration.ofHours(1) to R.string.snooze_1h,
    Duration.ofHours(3) to R.string.snooze_3h,
    Duration.ofDays(1) to R.string.snooze_tomorrow,
    Duration.ofDays(7) to R.string.snooze_next_week,
)

@Composable
internal fun SnoozeSection(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var open by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    Box {
        val label = draft.snoozedUntil
            ?.let { stringResource(R.string.snooze_until,
                DateTimeFormatter.ofPattern("MMM d, HH:mm").format(it.atZone(zone))) }
            ?: stringResource(R.string.snooze_none)
        Text(
            label,
            style = GsdType.body,
            color = gsd.tint,
            modifier = Modifier.clickable(role = Role.Button) { open = true }.testTag("snooze_row").padding(vertical = 4.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            snoozePresets.forEach { (duration, labelRes) ->
                DropdownMenuItem(
                    text = { Text(stringResource(labelRes), style = GsdType.body) },
                    onClick = {
                        open = false
                        onEdit { it.copy(snoozedUntil = Instant.now().plus(duration)) }
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.snooze_clear), style = GsdType.body) },
                onClick = {
                    open = false
                    onEdit { it.copy(snoozedUntil = null) }
                },
            )
        }
    }
}

@Composable
internal fun DependenciesSection(draft: Task, allTasks: List<Task>, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    val byId = allTasks.associateBy { it.id }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        draft.dependencies.mapNotNull { byId[it] }.forEach { blocker ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("• ${blocker.title}", style = GsdType.subhead, color = gsd.ink,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    onEdit { task -> task.copy(dependencies = task.dependencies - blocker.id) }
                }) {
                    Icon(Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.cd_remove_dependency, blocker.title),
                        tint = gsd.ink3, modifier = Modifier.size(14.dp))
                }
            }
        }
        val hint = stringResource(R.string.dependency_search_hint)
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            textStyle = GsdType.subhead.copy(color = gsd.ink),
            cursorBrush = gsd.cursor,
            modifier = Modifier.fillMaxWidth().testTag("dependency_search"),
            decorationBox = { inner -> HintedField(hint, query.isEmpty(), GsdType.subhead, inner) },
        )
        if (query.isNotEmpty()) {
            allTasks
                .filter { it.id != draft.id && it.id !in draft.dependencies }
                .filter { it.title.contains(query, ignoreCase = true) }
                .take(6)
                .forEach { candidate ->
                    val cycles = DependencyGraph.wouldCreateCycle(allTasks, draft.id, candidate.id)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GsdShapes.small)
                            .clickable(enabled = !cycles, role = Role.Button) {
                                onEdit { it.copy(dependencies = it.dependencies + candidate.id) }
                                query = ""
                            }
                            .testTag("dep_option_${candidate.id}")
                            .padding(8.dp),
                    ) {
                        Text(candidate.title, style = GsdType.body,
                            color = if (cycles) gsd.ink3 else gsd.ink, modifier = Modifier.weight(1f))
                        if (cycles) {
                            Text(stringResource(R.string.cycle_hint), style = GsdType.metadata, color = gsd.ink3)
                        }
                    }
                }
        }
        val blocking = DependencyGraph.blocking(allTasks, draft)
        if (blocking.isNotEmpty()) {
            Text(
                "${stringResource(R.string.editor_blocking)}: " + blocking.joinToString { it.title },
                style = GsdType.metadata,
                color = gsd.ink2,
            )
        }
    }
}
