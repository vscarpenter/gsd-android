package dev.vinny.gsd.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.ScreenTopBar
import dev.vinny.gsd.matrix.TaskActionsViewModel
import dev.vinny.gsd.matrix.UndoDeleteEffect
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.ui.HintedField
import dev.vinny.gsd.ui.SearchField
import dev.vinny.gsd.ui.reorderMotion
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** The shared description limit, in UTF-16 units (`TaskValidator`, web ADR 0018). */
private const val NOTES_LIMIT = 600

private val keepDateFormat = DateTimeFormatter.ofPattern("MMM d")

/** Saved (web ADR 0018; spec 2026-10-29 §3.6): tasks kept outside the matrix, with short notes. */
@Composable
fun SavedScreen(container: AppContainer, modifier: Modifier = Modifier, onUp: (() -> Unit)? = null) {
    val tasks by container.store.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val actions: TaskActionsViewModel =
        viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }
    Box(modifier = modifier.fillMaxSize()) {
        SavedContent(
            tasks = tasks,
            onEditNotes = { id, notes -> scope.launch { container.store.editNotes(id, notes) } },
            onReturn = { id, quadrant, due ->
                scope.launch { container.store.returnToMatrix(id, quadrant.urgent, quadrant.important, due) }
            },
            onDelete = actions::delete,
            onUp = onUp,
        )
        SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).imePadding())
    }
    UndoDeleteEffect(actions, snackbarHost)
}

@Composable
fun SavedContent(
    tasks: List<Task>,
    onEditNotes: (String, String) -> Unit,
    onReturn: (String, Quadrant, Instant?) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Task?>(null) }
    var returning by remember { mutableStateOf<Task?>(null) }
    val anySaved = tasks.any { it.isSaved }
    val visible = SavedPlacement.library(tasks, query)

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTopBar(stringResource(R.string.browse_saved), onUp = onUp)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(R.string.saved_search_hint),
                modifier = Modifier.padding(bottom = 8.dp).testTag("saved_search"),
            )
            when {
                !anySaved -> EmptyState(
                    icon = Icons.Outlined.BookmarkBorder,
                    title = stringResource(R.string.saved_empty),
                    body = stringResource(R.string.saved_empty_body),
                    modifier = Modifier.padding(top = 40.dp),
                )
                visible.isEmpty() -> SearchMissEmptyState(query.trim())
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { task ->
                        SavedRow(
                            task = task,
                            onReturn = { returning = task },
                            onEditNotes = { editing = task },
                            onDelete = { onDelete(task.id) },
                            modifier = reorderMotion(),
                        )
                    }
                }
            }
        }
    }

    editing?.let { task ->
        NotesDialog(
            task = task,
            onSave = { notes -> onEditNotes(task.id, notes); editing = null },
            onDismiss = { editing = null },
        )
    }
    returning?.let { task ->
        ReturnDialog(
            task = task,
            today = today,
            zone = zone,
            onConfirm = { quadrant, due -> onReturn(task.id, quadrant, due); returning = null },
            onDismiss = { returning = null },
        )
    }
}

/** On the muted surface, not a quadrant wash: a Saved item has no place in the matrix (web Saved list). */
@Composable
private fun SavedRow(
    task: Task,
    onReturn: () -> Unit,
    onEditNotes: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GsdShapes.card)
            .background(gsd.surface2)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("saved_row_${task.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(task.title, style = GsdType.cardTitle, color = gsd.ink)
        if (task.description.isNotBlank()) {
            Text(task.description, style = GsdType.subhead, color = gsd.ink2, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        if (task.tags.isNotEmpty()) {
            Text(task.tags.joinToString(" ") { "#$it" }, style = GsdType.metadata, color = gsd.ink3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onReturn) {
                Text(stringResource(R.string.saved_return), style = GsdType.metadata, color = gsd.tint)
            }
            TextButton(onClick = onEditNotes) {
                Text(stringResource(R.string.saved_edit_notes), style = GsdType.metadata, color = gsd.tint)
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.menu_delete), style = GsdType.metadata, color = gsd.alert)
            }
        }
    }
}

@Composable
private fun NotesDialog(task: Task, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val gsd = LocalGsdColors.current
    var notes by remember(task.id) { mutableStateOf(task.description) }
    val hint = stringResource(R.string.saved_notes_hint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_edit_notes), style = GsdType.cardTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BasicTextField(
                    value = notes,
                    // Capped here, so the store's 600-unit check never rejects a save
                    onValueChange = { notes = it.take(NOTES_LIMIT) },
                    textStyle = GsdType.body.copy(color = gsd.ink),
                    cursorBrush = gsd.cursor,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).testTag("saved_notes_field"),
                    decorationBox = { inner -> HintedField(hint, notes.isEmpty(), GsdType.body, inner) },
                )
                Text(
                    stringResource(R.string.saved_notes_count, notes.length, NOTES_LIMIT),
                    style = GsdType.caption,
                    color = gsd.ink3,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(notes) }, modifier = Modifier.testTag("saved_notes_save")) {
                Text(stringResource(R.string.editor_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

/** Return reviews the quadrant and the date before the task rejoins the matrix (iOS ADR 0001). */
@Composable
internal fun ReturnDialog(
    task: Task,
    today: LocalDate,
    zone: ZoneId,
    onConfirm: (Quadrant, Instant?) -> Unit,
    onDismiss: () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val choices = ReturnDue.choicesFor(task.dueDate)
    var quadrant by remember(task.id) { mutableStateOf(task.quadrant) }
    var due by remember(task.id) { mutableStateOf(choices.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_return_title, task.title), style = GsdType.cardTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.editor_section_quadrant), style = GsdType.metadata, color = gsd.ink2)
                Quadrant.entries.forEach { option ->
                    ChoiceRow(option.title, selected = option == quadrant) { quadrant = option }
                }
                Text(
                    stringResource(R.string.saved_return_due),
                    style = GsdType.metadata,
                    color = gsd.ink2,
                    modifier = Modifier.padding(top = 12.dp),
                )
                choices.forEach { option ->
                    ChoiceRow(dueLabel(option, task.dueDate, zone), selected = option == due) { due = option }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(quadrant, due.resolve(task.dueDate, today, zone)) },
                modifier = Modifier.testTag("saved_return_confirm"),
            ) { Text(stringResource(R.string.saved_return_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) }
        },
    )
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, style = GsdType.body, color = LocalGsdColors.current.ink)
    }
}

@Composable
private fun dueLabel(choice: ReturnDue, current: Instant?, zone: ZoneId): String = when (choice) {
    ReturnDue.KEEP -> stringResource(
        R.string.saved_due_keep,
        current?.atZone(zone)?.format(keepDateFormat).orEmpty(),
    )
    ReturnDue.NONE -> stringResource(R.string.due_none)
    ReturnDue.TODAY -> stringResource(R.string.due_today)
    ReturnDue.THIS_WEEK -> stringResource(R.string.due_this_week)
    ReturnDue.NEXT_WEEK -> stringResource(R.string.due_next_week)
}
