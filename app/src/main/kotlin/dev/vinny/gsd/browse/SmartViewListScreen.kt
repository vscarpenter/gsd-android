package dev.vinny.gsd.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
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
import dev.vinny.gsd.matrix.TaskActionsViewModel
import dev.vinny.gsd.matrix.TaskBadges
import dev.vinny.gsd.matrix.TaskRowActions
import dev.vinny.gsd.matrix.TaskRowWithMenu
import dev.vinny.gsd.matrix.UndoDeleteEffect
import dev.vinny.gsd.matrix.taskRowActions
import dev.vinny.gsd.model.DependencyGraph
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskOrdering
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.ZoneId

/** A smart view's flat filtered list with in-list search (spec §8.13). */
@Composable
fun SmartViewListScreen(container: AppContainer, viewId: String, modifier: Modifier = Modifier) {
    val browseViewModel: BrowseViewModel =
        viewModel { BrowseViewModel(container.store, container.smartViewStore) }
    val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }
    val state by browseViewModel.state.collectAsStateWithLifecycle()
    val editorDraft by editorViewModel.draft.collectAsStateWithLifecycle()
    val view = (state.pinned + state.builtIn + state.custom).map { it.view }.firstOrNull { it.id == viewId }
        ?: return
    val allTasks by browseViewModel.tasks.collectAsStateWithLifecycle()
    val actions: TaskActionsViewModel =
        viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize()) {
        ViewListContent(
            view = view,
            allTasks = allTasks,
            rowActions = { task ->
                taskRowActions(task, context, actions, onEdit = { editorViewModel.openExisting(task.id) })
            },
        )
        SnackbarHost(hostState = snackbarHost, modifier = Modifier.align(Alignment.BottomCenter))
    }
    UndoDeleteEffect(actions, snackbarHost)
    if (editorDraft != null) {
        TaskEditorSheet(viewModel = editorViewModel, onDismiss = {})
    }
}

@Composable
fun ViewListContent(
    view: SmartView,
    allTasks: List<Task>?,
    rowActions: (Task) -> TaskRowActions,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    val now = Instant.now()
    val zone = ZoneId.systemDefault()
    val effective = if (query.isEmpty()) view.criteria else view.criteria.copy(searchQuery = query)
    val matches = allTasks
        ?.filter { effective.matches(it, allTasks, now, zone) }
        ?.let { TaskOrdering.forList(it, effective.status) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(view.name, style = GsdType.quadrantTitle, color = smartViewTint(view.id),
            modifier = Modifier.padding(vertical = 12.dp))
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            if (query.isEmpty()) {
                Text(stringResource(R.string.view_search_hint), style = GsdType.subhead, color = gsd.ink3)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = GsdType.subhead.copy(color = gsd.ink),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("view_search"),
            )
        }
        if (matches == null) {
            TaskListSkeleton()
        } else if (matches.isEmpty()) {
            if (query.isNotEmpty()) {
                SearchMissEmptyState(query.trim())
            } else {
                val emptyRes = when (view.id) {
                    "overdue" -> R.string.view_empty_overdue
                    "weeks-wins" -> R.string.view_empty_wins
                    else -> R.string.view_empty_generic
                }
                Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(emptyRes), style = GsdType.subhead, color = gsd.ink2)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(matches, key = { it.id }) { task ->
                    val badges = TaskBadges(
                        blocked = DependencyGraph.uncompletedBlockers(allTasks.orEmpty(), task).size,
                        blocking = DependencyGraph.blocking(allTasks.orEmpty(), task).size,
                    )
                    TaskRowWithMenu(task = task, badges = badges, actions = rowActions(task))
                }
            }
        }
    }
}
