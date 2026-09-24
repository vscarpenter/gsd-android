package dev.vinny.gsd.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.editor.TaskEditorSheet
import dev.vinny.gsd.editor.TaskEditorViewModel
import dev.vinny.gsd.matrix.ConfettiOverlay
import dev.vinny.gsd.matrix.SwipeTaskRow
import dev.vinny.gsd.matrix.TaskActionsViewModel
import dev.vinny.gsd.matrix.TaskBadges
import dev.vinny.gsd.matrix.TaskRowActions
import dev.vinny.gsd.matrix.TaskRowWithMenu
import dev.vinny.gsd.matrix.UndoDeleteEffect
import dev.vinny.gsd.matrix.rememberCompletionCelebration
import dev.vinny.gsd.matrix.taskRowActions
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.FilterCriteria
import dev.vinny.gsd.model.FilterStatus
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskOrdering
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import java.time.Instant
import java.time.ZoneId

/** Icon token → vector for both built-ins (§5.7) and the 10 curated custom options. */
val smartViewIcons: Map<String, ImageVector> = mapOf(
    "adjust" to Icons.Outlined.AdsClick,
    "calendar_month" to Icons.Outlined.CalendarMonth,
    "warning" to Icons.Outlined.Warning,
    "event_busy" to Icons.Outlined.EventBusy,
    "auto_awesome" to Icons.Outlined.AutoAwesome,
    "verified" to Icons.Outlined.Verified,
    "check_circle" to Icons.Outlined.CheckCircle,
    "repeat" to Icons.Outlined.Repeat,
    "bolt" to Icons.Outlined.Bolt,
    "label" to Icons.Outlined.Label,
    "star" to Icons.Outlined.Star,
    "flag" to Icons.Outlined.Flag,
    "folder" to Icons.Outlined.Folder,
    "favorite" to Icons.Outlined.Favorite,
    "lightbulb" to Icons.Outlined.Lightbulb,
    "work" to Icons.Outlined.Work,
    "home" to Icons.Outlined.Home,
    "book" to Icons.Outlined.Book,
)

/** The 10 curated options for custom views (spec §7.6). */
val curatedCustomIcons: List<String> =
    listOf("bolt", "label", "star", "flag", "folder", "favorite", "lightbulb", "work", "home", "book")

/** Views whose icon takes quadrant meaning (spec §5.7); the rest render ink2. */
@Composable
fun smartViewTint(viewId: String): Color {
    val gsd = LocalGsdColors.current
    return when (viewId) {
        "today-focus" -> gsd.rust
        "this-week", "ready-to-work" -> gsd.tide
        "overdue" -> gsd.alert
        "weeks-wins" -> gsd.ochre
        else -> gsd.ink2
    }
}

@Composable
fun BrowseScreen(
    container: AppContainer,
    onOpenArchive: () -> Unit,
    onOpenTrash: () -> Unit,
    onOpenView: (String) -> Unit,
    onNewView: () -> Unit,
    onEditView: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = viewModel { BrowseViewModel(container.store, container.smartViewStore) }
    val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editorDraft by editorViewModel.draft.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val trashed by container.trashStore.observeTrash()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val actions: TaskActionsViewModel =
        viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val celebration = rememberCompletionCelebration()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        BrowseContent(
            state = state,
            onOpenArchive = onOpenArchive,
            trashCount = trashed.size,
            onOpenTrash = onOpenTrash,
            onOpenView = onOpenView,
            onNewView = onNewView,
            onTogglePin = viewModel::togglePin,
            onEditView = onEditView,
            onDeleteView = viewModel::deleteCustom,
            tasks = tasks,
            rowActions = { task ->
                taskRowActions(task, context, actions, celebration, onEdit = { editorViewModel.openExisting(task.id) })
            },
        )
        ConfettiOverlay(trigger = celebration.confettiTrigger)
        SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
    }
    UndoDeleteEffect(actions, snackbarHost)
    if (editorDraft != null) {
        TaskEditorSheet(
            viewModel = editorViewModel,
            reminderSettings = container.notificationSettingsStore,
            onDismiss = {},
        )
    }
}

@Composable
fun BrowseContent(
    state: BrowseState,
    onOpenArchive: () -> Unit,
    trashCount: Int,
    onOpenTrash: () -> Unit,
    onOpenView: (String) -> Unit,
    onNewView: () -> Unit,
    onTogglePin: (String) -> Unit,
    onEditView: (String) -> Unit,
    onDeleteView: (String) -> Unit,
    tasks: List<Task>? = null,
    rowActions: (Task) -> TaskRowActions = { TaskRowActions() },
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()
    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 4.dp)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.browse_search_hint), style = GsdType.subhead, color = gsd.ink3)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = GsdType.subhead.copy(color = gsd.ink),
                cursorBrush = gsd.cursor,
                modifier = Modifier.fillMaxWidth().testTag("browse_search"),
            )
        }
        if (trimmed.isNotEmpty()) {
            BrowseSearchResults(
                query = trimmed,
                allTasks = tasks.orEmpty(),
                rowActions = rowActions,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "archive") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenArchive).padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Inventory2, contentDescription = null, tint = gsd.ink2)
                        Text(stringResource(R.string.browse_archive), style = GsdType.cardTitle, color = gsd.ink)
                    }
                }
                item(key = "trash") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenTrash).padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = gsd.ink2)
                        Text(
                            stringResource(R.string.trash_row_label),
                            style = GsdType.cardTitle,
                            color = gsd.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "$trashCount",
                            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
                            color = gsd.ink2,
                        )
                    }
                }
                section(R.string.browse_pinned, state.pinned, onOpenView, onTogglePin, onEditView, onDeleteView)
                section(R.string.browse_built_in, state.builtIn, onOpenView, onTogglePin, onEditView, onDeleteView)
                section(R.string.browse_custom, state.custom, onOpenView, onTogglePin, onEditView, onDeleteView)
                item(key = "new-view") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable(onClick = onNewView).padding(vertical = 12.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = gsd.tint)
                        Text(stringResource(R.string.browse_new_view), style = GsdType.body, color = gsd.tint)
                    }
                }
            }
        }
    }
}

/** Root search over every active task, shared list order (spec §3.4). */
@Composable
fun BrowseSearchResults(
    query: String,
    allTasks: List<Task>,
    rowActions: (Task) -> TaskRowActions,
    modifier: Modifier = Modifier,
) {
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val criteria = FilterCriteria(status = FilterStatus.ACTIVE, searchQuery = query)
    val matches = TaskOrdering.forList(allTasks.filter { criteria.matches(it, allTasks, now, zone) }, FilterStatus.ACTIVE)
    if (matches.isEmpty()) {
        SearchMissEmptyState(query, modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(matches, key = { it.id }) { task ->
            val badges = TaskBadges(
                blocked = DependencyGraph.uncompletedBlockers(allTasks, task).size,
                blocking = DependencyGraph.blocking(allTasks, task).size,
            )
            val actions = rowActions(task)
            SwipeTaskRow(task = task, onComplete = actions.onToggleComplete, onDelete = actions.onDelete) {
                TaskRowWithMenu(task = task, badges = badges, actions = actions)
            }
        }
    }
}

/** §5.10 empty state for a search miss; shared by Browse search and the in-view search (S§3.4). */
@Composable
fun SearchMissEmptyState(query: String, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.Search,
        title = stringResource(R.string.search_empty_title, query),
        body = stringResource(R.string.search_empty_body),
        modifier = modifier.padding(top = 40.dp),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    labelRes: Int,
    rows: List<BrowseRow>,
    onOpenView: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onEditView: (String) -> Unit,
    onDeleteView: (String) -> Unit,
) {
    if (rows.isEmpty()) return
    item(key = "header-$labelRes") {
        Text(
            stringResource(labelRes).uppercase(),
            style = GsdType.caption,
            color = LocalGsdColors.current.ink3,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    items(rows, key = { it.view.id }) { row ->
        SmartViewRow(row, onOpenView, onTogglePin, onEditView, onDeleteView)
    }
}

@Composable
private fun SmartViewRow(
    row: BrowseRow,
    onOpenView: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onEditView: (String) -> Unit,
    onDeleteView: (String) -> Unit,
) {
    val gsd = LocalGsdColors.current
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onOpenView(row.view.id) }.padding(vertical = 8.dp),
    ) {
        Icon(
            smartViewIcons[row.view.icon] ?: Icons.Outlined.Bolt,
            contentDescription = null,
            tint = smartViewTint(row.view.id),
            modifier = Modifier.size(22.dp),
        )
        Text(row.view.name, style = GsdType.body, color = gsd.ink, modifier = Modifier.weight(1f))
        Text(
            "${row.count}",
            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
            color = gsd.ink2,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Outlined.MoreHoriz,
                    contentDescription = stringResource(R.string.cd_task_options),
                    tint = gsd.ink3,
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(if (row.pinned) R.string.view_menu_unpin else R.string.view_menu_pin),
                            style = GsdType.body,
                        )
                    },
                    onClick = { menuOpen = false; onTogglePin(row.view.id) },
                )
                if (!row.view.isBuiltIn) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_menu_edit), style = GsdType.body) },
                        onClick = { menuOpen = false; onEditView(row.view.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.view_menu_delete), style = GsdType.body) },
                        onClick = { menuOpen = false; onDeleteView(row.view.id) },
                    )
                }
            }
        }
    }
}
