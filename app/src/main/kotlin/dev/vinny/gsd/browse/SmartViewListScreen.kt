package dev.vinny.gsd.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EmojiEvents
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
import dev.vinny.gsd.matrix.QuickSnooze
import dev.vinny.gsd.matrix.ScreenTopBar
import dev.vinny.gsd.matrix.SwipeTaskRow
import dev.vinny.gsd.matrix.TaskActionsViewModel
import dev.vinny.gsd.matrix.TaskBadges
import dev.vinny.gsd.matrix.TaskRowActions
import dev.vinny.gsd.matrix.TaskRowWithMenu
import dev.vinny.gsd.matrix.UndoDeleteEffect
import dev.vinny.gsd.matrix.UndoSaveEffect
import dev.vinny.gsd.matrix.UndoUrgentEffect
import dev.vinny.gsd.matrix.rememberCompletionCelebration
import dev.vinny.gsd.matrix.taskRowActions
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskOrdering
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.ui.SearchField
import dev.vinny.gsd.ui.reorderMotion
import java.time.Instant
import java.time.ZoneId

/** A smart view's flat filtered list with in-list search (spec §8.13). */
@Composable
fun SmartViewListScreen(
    container: AppContainer,
    viewId: String,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    val browseViewModel: BrowseViewModel =
        viewModel { BrowseViewModel(container.store, container.smartViewStore) }
    val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }
    val state by browseViewModel.state.collectAsStateWithLifecycle()
    val editorDraft by editorViewModel.draft.collectAsStateWithLifecycle()
    val view = (state.pinned + state.builtIn + state.custom).map { it.view }.firstOrNull { it.id == viewId }
    if (view == null) {
        // Until the view loads, or if it was deleted, the bar still offers the way back
        ScreenTopBar("", modifier = modifier, onUp = onUp)
        return
    }
    val allTasks by browseViewModel.tasks.collectAsStateWithLifecycle()
    val actions: TaskActionsViewModel =
        viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val celebration = rememberCompletionCelebration()
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        ViewListContent(
            view = view,
            allTasks = allTasks,
            rowActions = { task ->
                taskRowActions(task, context, actions, celebration, onEdit = { editorViewModel.openExisting(task.id) })
            },
            onUp = onUp,
        )
        ConfettiOverlay(trigger = celebration.confettiTrigger)
        // Above the keyboard: a delete from search results offers Undo there (spec §8.20)
        SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter).imePadding())
    }
    UndoDeleteEffect(actions, snackbarHost)
    UndoSaveEffect(actions, snackbarHost)
    UndoUrgentEffect(actions, snackbarHost)
    if (editorDraft != null) {
        TaskEditorSheet(
            viewModel = editorViewModel,
            reminderSettings = container.notificationSettingsStore,
            onDismiss = {},
        )
    }
}

@Composable
fun ViewListContent(
    view: SmartView,
    allTasks: List<Task>?,
    rowActions: (Task) -> TaskRowActions,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val effective = if (query.isEmpty()) view.criteria else view.criteria.copy(searchQuery = query)
    val matches = allTasks
        ?.filter { effective.matches(it, allTasks, now, zone) }
        ?.let { TaskOrdering.forList(it, effective.status) }
    Column(modifier = modifier.fillMaxSize()) {
        // The view's name heads its own bar, with an Up arrow when pushed (finding 21)
        ScreenTopBar(view.name, onUp = onUp)
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            SearchField(
                value = query,
                onValueChange = { query = it },
                hint = stringResource(R.string.view_search_hint),
                modifier = Modifier.padding(bottom = 8.dp).testTag("view_search"),
            )
            if (matches == null) {
                TaskListSkeleton()
            } else if (matches.isEmpty()) {
                if (query.isNotEmpty()) {
                    SearchMissEmptyState(query.trim())
                } else {
                    // §5.10 view-aware empty states, worded as on iOS (finding 32)
                    val emptyModifier = Modifier.padding(top = 40.dp)
                    when (view.id) {
                        "overdue" -> EmptyState(
                            icon = Icons.Outlined.CheckCircle,
                            title = stringResource(R.string.view_empty_overdue),
                            body = stringResource(R.string.view_empty_overdue_body),
                            modifier = emptyModifier,
                            iconTint = gsd.success, // the one reassuring tint §5.10 allows
                        )
                        "weeks-wins" -> EmptyState(
                            icon = Icons.Outlined.EmojiEvents,
                            title = stringResource(R.string.view_empty_wins),
                            body = stringResource(R.string.view_empty_wins_body),
                            modifier = emptyModifier,
                        )
                        else -> EmptyState(
                            icon = smartViewIcons[view.icon] ?: Icons.Outlined.Bolt,
                            title = stringResource(R.string.view_empty_generic),
                            body = stringResource(R.string.view_empty_generic_body, view.name),
                            modifier = emptyModifier,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(matches, key = { it.id }) { task ->
                        val badges = TaskBadges(
                            blocked = DependencyGraph.uncompletedBlockers(allTasks.orEmpty(), task).size,
                            blocking = DependencyGraph.blocking(allTasks.orEmpty(), task).size,
                        )
                        val actions = rowActions(task)
                        SwipeTaskRow(
                            task = task,
                            onComplete = actions.onToggleComplete,
                            onDelete = actions.onDelete,
                            modifier = reorderMotion(),
                            onSnooze = { actions.onSnooze(QuickSnooze) },
                            onMarkUrgent = actions.onMarkUrgent,
                        ) {
                            TaskRowWithMenu(task = task, badges = badges, actions = actions)
                        }
                    }
                }
            }
        }
    }
}
