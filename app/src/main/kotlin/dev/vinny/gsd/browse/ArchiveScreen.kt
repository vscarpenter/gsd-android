package dev.vinny.gsd.browse

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.ScreenTopBar
import dev.vinny.gsd.matrix.TaskCard
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.ui.SearchField
import dev.vinny.gsd.ui.reorderMotion
import kotlinx.coroutines.launch

/** Archive surface (spec §8.12): dimmed read-only cards, restore, permanent delete. */
@Composable
fun ArchiveScreen(container: AppContainer, modifier: Modifier = Modifier, onUp: (() -> Unit)? = null) {
    val archived by container.archiveStore.observeArchived()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by container.db.archiveSettingsDao().observe()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    ArchiveContent(
        archived = archived,
        autoArchiveEnabled = settings?.enabled ?: false,
        archiveAfterDays = settings?.archiveAfterDays ?: 30,
        onRestore = { id -> scope.launch { container.archiveStore.restore(id) } },
        onDeletePermanently = { id -> scope.launch { container.archiveStore.deletePermanently(id) } },
        onSetAutoArchive = { enabled, days ->
            scope.launch {
                container.db.archiveSettingsDao().upsert(
                    dev.vinny.gsd.store.ArchiveSettingsEntity(enabled = enabled, archiveAfterDays = days),
                )
            }
        },
        onArchiveNow = { scope.launch { container.archiveStore.sweep(settings?.archiveAfterDays ?: 30) } },
        modifier = modifier,
        onUp = onUp,
    )
}

@Composable
fun ArchiveContent(
    archived: List<Task>,
    autoArchiveEnabled: Boolean,
    archiveAfterDays: Int,
    onRestore: (String) -> Unit,
    onDeletePermanently: (String) -> Unit,
    onSetAutoArchive: (Boolean, Int) -> Unit,
    onArchiveNow: () -> Unit,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<Task?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val visible = archived.filter { query.isEmpty() || query.lowercase() in it.title.lowercase() }

    Column(modifier = modifier.fillMaxSize()) {
        // The title row became the shared bar; its menu rides in the bar's actions (finding 21)
        ScreenTopBar(stringResource(R.string.browse_archive), onUp = onUp) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.archive_options), tint = gsd.ink3)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (autoArchiveEnabled) R.string.archive_auto_off else R.string.archive_auto_on,
                                ),
                                style = GsdType.body,
                            )
                        },
                        onClick = { menuOpen = false; onSetAutoArchive(!autoArchiveEnabled, archiveAfterDays) },
                    )
                    listOf(30, 60, 90).forEach { days ->
                        val chosen = days == archiveAfterDays
                        // A check icon and a selected state, not a " ✓" glyph in the text (finding 29)
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive_after_days, days), style = GsdType.body) },
                            onClick = { menuOpen = false; onSetAutoArchive(autoArchiveEnabled, days) },
                            modifier = Modifier.semantics { selected = chosen },
                            trailingIcon = if (chosen) {
                                { Icon(Icons.Outlined.Check, contentDescription = null, tint = gsd.tint) }
                            } else {
                                null
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.archive_now), style = GsdType.body) },
                        onClick = { menuOpen = false; onArchiveNow() },
                    )
                }
            }
        }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(R.string.archive_search_hint),
                modifier = Modifier.padding(bottom = 8.dp).testTag("archive_search"),
            )
            // §5.10 empty states (finding 32). A search that matches nothing says so, not "Nothing archived".
            if (archived.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Inventory2,
                    title = stringResource(R.string.archive_empty),
                    body = stringResource(R.string.archive_empty_body),
                    modifier = Modifier.padding(top = 40.dp),
                )
            } else if (visible.isEmpty()) {
                SearchMissEmptyState(query.trim())
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visible, key = { it.id }) { task ->
                        // Not dimmed: 72% alpha took the text and buttons under AA (review, Accessibility)
                        Column(modifier = reorderMotion()) {
                            TaskCard(task = task, onToggleComplete = null, onClick = {})
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                TextButton(onClick = { onRestore(task.id) }) {
                                    Text(stringResource(R.string.archive_restore), style = GsdType.metadata, color = gsd.tint)
                                }
                                TextButton(onClick = { confirmDelete = task }) {
                                    Text(stringResource(R.string.archive_delete), style = GsdType.metadata, color = gsd.alert)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.archive_delete_title, task.title), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.archive_delete_body), style = GsdType.body) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePermanently(task.id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.archive_delete), color = gsd.alert) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            },
        )
    }
}
