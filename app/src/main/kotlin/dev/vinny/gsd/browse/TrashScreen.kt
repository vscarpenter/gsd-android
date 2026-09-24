package dev.vinny.gsd.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.SwipeTaskRow
import dev.vinny.gsd.matrix.TaskCard
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.EmptyState
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

/**
 * Trash surface (parity spec P§3.2): dimmed read-only cards, restore, permanent
 * delete, 30-day retention countdown. Restore re-activates the task preserving
 * its original timestamps (a faithful undo) — see [dev.vinny.gsd.store.TrashStore].
 */
@Composable
fun TrashScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val trashed by container.trashStore.observeTrash()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    TrashContent(
        trashed = trashed,
        now = container.clock.instant(),
        onRestore = { id -> scope.launch { container.trashStore.restore(id) } },
        onDeleteForever = { id -> scope.launch { container.trashStore.deleteForever(id) } },
        onEmptyTrash = { scope.launch { container.trashStore.empty() } },
        modifier = modifier,
    )
}

@Composable
fun TrashContent(
    trashed: List<Pair<Task, Instant>>,
    now: Instant,
    onRestore: (String) -> Unit,
    onDeleteForever: (String) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDeleteForever by remember { mutableStateOf<Task?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmBulkDelete by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<String>()) }

    fun exitSelection() {
        selectionMode = false
        selection = emptySet()
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.trash_title),
                style = GsdType.quadrantTitle,
                color = gsd.ink,
                modifier = Modifier.weight(1f),
            )
            if (trashed.isNotEmpty()) {
                IconButton(onClick = { if (selectionMode) exitSelection() else selectionMode = true }) {
                    Icon(
                        Icons.Outlined.Checklist,
                        contentDescription = stringResource(R.string.cd_select_mode),
                        tint = gsd.ink2,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.trash_options),
                        tint = gsd.ink3,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.trash_empty_action), style = GsdType.body) },
                        onClick = { menuOpen = false; confirmEmpty = true },
                    )
                }
            }
        }

        if (selectionMode && selection.isNotEmpty()) {
            TrashBulkBar(
                selectedCount = selection.size,
                onRestore = { selection.forEach(onRestore); exitSelection() },
                onDeleteForever = { confirmBulkDelete = true },
            )
        }

        if (trashed.isEmpty()) {
            // The §5.10 empty state (finding 32)
            EmptyState(
                icon = Icons.Outlined.DeleteOutline,
                title = stringResource(R.string.trash_empty_state),
                body = stringResource(R.string.trash_empty_body),
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(trashed, key = { it.first.id }) { (task, deletedAt) ->
                    val isSelected = task.id in selection
                    fun toggleSelected() {
                        selection = if (isSelected) selection - task.id else selection + task.id
                    }
                    val row: @Composable () -> Unit = {
                        TrashRow(
                            task = task,
                            deletedAt = deletedAt,
                            now = now,
                            selectionMode = selectionMode,
                            onClick = { if (selectionMode) toggleSelected() },
                            onRestore = { onRestore(task.id) },
                            onDeleteForever = { confirmDeleteForever = task },
                        )
                    }
                    if (selectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Checkbox(checked = isSelected, onCheckedChange = { toggleSelected() })
                            Box(modifier = Modifier.weight(1f)) { row() }
                        }
                    } else {
                        SwipeTaskRow(
                            task = task,
                            onComplete = { onRestore(task.id) },
                            onDelete = { confirmDeleteForever = task },
                            content = row,
                        )
                    }
                }
            }
        }
    }

    confirmDeleteForever?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmDeleteForever = null },
            // It names the task, so the dialog says what goes (finding 31)
            title = { Text(stringResource(R.string.trash_delete_title, task.title), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.archive_delete_body), style = GsdType.body) },
            confirmButton = {
                TextButton(onClick = { onDeleteForever(task.id); confirmDeleteForever = null }) {
                    Text(stringResource(R.string.trash_delete_forever), color = gsd.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteForever = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_confirm), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.archive_delete_body), style = GsdType.body) },
            confirmButton = {
                TextButton(onClick = { onEmptyTrash(); confirmEmpty = false }) {
                    Text(stringResource(R.string.trash_empty_action), color = gsd.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }

    if (confirmBulkDelete) {
        AlertDialog(
            onDismissRequest = { confirmBulkDelete = false },
            title = { Text(stringResource(R.string.bulk_delete_confirm, selection.size), style = GsdType.cardTitle) },
            confirmButton = {
                TextButton(onClick = {
                    selection.forEach(onDeleteForever)
                    confirmBulkDelete = false
                    exitSelection()
                }) { Text(stringResource(R.string.trash_delete_forever), color = gsd.alert) }
            },
            dismissButton = {
                TextButton(onClick = { confirmBulkDelete = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun TrashRow(
    task: Task,
    deletedAt: Instant,
    now: Instant,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val (label, color) = trashRetentionLabel(deletedAt, now)
    Column(modifier = Modifier.alpha(0.72f)) {
        TaskCard(task = task, onToggleComplete = null, onClick = onClick)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(label, style = GsdType.metadata, color = color, modifier = Modifier.weight(1f))
            if (!selectionMode) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.trash_restore), style = GsdType.metadata, color = gsd.tint)
                }
                TextButton(onClick = onDeleteForever) {
                    Text(stringResource(R.string.trash_delete_forever), style = GsdType.metadata, color = gsd.alert)
                }
            }
        }
    }
}

@Composable
private fun TrashBulkBar(
    selectedCount: Int,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(gsd.surface2)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.bulk_selected, selectedCount), style = GsdType.metadata, color = gsd.ink2)
        TextButton(onClick = onRestore) {
            Text(stringResource(R.string.trash_restore), style = GsdType.metadata, color = gsd.tint)
        }
        TextButton(onClick = onDeleteForever) {
            Text(stringResource(R.string.trash_delete_forever), style = GsdType.metadata, color = gsd.alert)
        }
    }
}

/** Retention countdown (parity spec P§3): 30 days from [deletedAt], start-of-day cutoff. */
@Composable
internal fun trashRetentionLabel(
    deletedAt: Instant,
    now: Instant,
): Pair<String, androidx.compose.ui.graphics.Color> {
    val gsd = LocalGsdColors.current
    val zone = ZoneId.systemDefault()
    val expiryDay = deletedAt.atZone(zone).toLocalDate().plusDays(30)
    val today = now.atZone(zone).toLocalDate()
    val daysLeft = ChronoUnit.DAYS.between(today, expiryDay)
    return if (daysLeft <= 0) {
        stringResource(R.string.trash_last_day) to gsd.alert
    } else {
        stringResource(R.string.trash_days_left, daysLeft) to gsd.ink3
    }
}
