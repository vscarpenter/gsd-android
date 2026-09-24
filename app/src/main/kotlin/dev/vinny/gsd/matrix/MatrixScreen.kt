package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.capture.CaptureBarContent
import dev.vinny.gsd.capture.CaptureViewModel
import dev.vinny.gsd.editor.TaskEditorSheet
import dev.vinny.gsd.editor.TaskEditorViewModel
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.palette.ConsumeWhileResumed
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

@Composable
fun MatrixScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    onNavigate: (String) -> Unit = {},
) {
    val viewModel: MatrixViewModel =
        viewModel { MatrixViewModel(container.store, container.prefs) }
    val actions: TaskActionsViewModel =
        viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val captureViewModel: CaptureViewModel = viewModel { CaptureViewModel(container.store) }
    val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val editorDraft by editorViewModel.draft.collectAsStateWithLifecycle()
    val celebration = rememberCompletionCelebration()
    val snackbarHost = remember { SnackbarHostState() }
    val showCompleted by container.prefs.showCompleted.collectAsStateWithLifecycle(initialValue = true)
    val scope = rememberCoroutineScope()
    var paletteVisible by remember { mutableStateOf(false) }
    ConsumeWhileResumed(container.palette.openRequests) { paletteVisible = true }
    ConsumeWhileResumed(container.palette.newTaskRequests) { editorViewModel.openNew(null) }
    val appContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        container.palette.openTaskRequests.collect { taskId ->
            if (taskId != null) {
                editorViewModel.openExisting(taskId, onMissing = {
                    android.widget.Toast.makeText(
                        appContext,
                        appContext.getString(R.string.task_not_found),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                })
                container.palette.openTaskRequests.value = null
            }
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            GsdTopBar(
                screenName = stringResource(R.string.nav_matrix),
                showCompleted = showCompleted,
                onToggleShowCompleted = { scope.launch { container.prefs.setShowCompleted(!showCompleted) } },
                onSearch = { paletteVisible = true },
                onToggleSelectMode = viewModel::toggleSelectionMode,
                syncChip = {
                    val signedIn by container.syncCoordinator.signedIn.collectAsStateWithLifecycle()
                    val syncing by container.syncCoordinator.syncing.collectAsStateWithLifecycle()
                    val pending by container.syncCoordinator.pendingCount
                        .collectAsStateWithLifecycle(initialValue = 0)
                    val failedItems by container.syncCoordinator.failedCount
                        .collectAsStateWithLifecycle(initialValue = 0)
                    SyncStatusChip(
                        SyncChipState(
                            signedIn = signedIn,
                            syncing = syncing,
                            pending = pending,
                            failed = failedItems,
                        ),
                    )
                },
            )
            CaptureBarContent(
                state = captureState,
                onTextChange = captureViewModel::onTextChange,
                onCycleOverride = captureViewModel::cycleOverride,
                onSubmit = { captureViewModel.submit(onError = {}) },
                onDetails = { editorViewModel.openNew(captureViewModel.draftForEditor()) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            val onToggle: (dev.vinny.gsd.model.Task) -> Unit = { task ->
                celebration.onToggle(task)
                actions.toggleCompleted(task.id)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
            val selection by viewModel.selection.collectAsStateWithLifecycle()
            val rowActions: (dev.vinny.gsd.model.Task) -> TaskRowActions = if (selectionMode) {
                { task ->
                    TaskRowActions(
                        onEdit = { viewModel.toggleSelected(task.id) },
                        onToggleComplete = { viewModel.toggleSelected(task.id) },
                    )
                }
            } else { task ->
                taskRowActions(
                    task = task,
                    context = context,
                    actions = actions,
                    celebration = celebration,
                    onEdit = { editorViewModel.openExisting(task.id) },
                )
            }
            if (selectionMode && selection.isNotEmpty()) {
                val selectedTags = sections.orEmpty().flatMap { it.tasks }
                    .filter { it.id in selection }
                    .flatMap { it.tags }
                    .distinct()
                BulkActionBar(
                    selectedCount = selection.size,
                    selectionTags = selectedTags,
                    onRun = viewModel::runBulk,
                )
            }
            if (expanded) {
                val currentSections = sections
                if (currentSections == null) {
                    MatrixSkeleton(expanded = true, modifier = Modifier.weight(1f))
                } else {
                    MatrixGrid(
                        sections = currentSections,
                        onToggleComplete = onToggle,
                        onOpen = editorViewModel::openExisting,
                        onDropToQuadrant = actions::moveToQuadrant,
                        onDelete = { actions.delete(it.id) },
                        rowActions = rowActions,
                        menuEnabled = !selectionMode,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                MatrixList(
                    sections = sections,
                    onToggleComplete = onToggle,
                    onOpen = editorViewModel::openExisting,
                    onDelete = { actions.delete(it.id) },
                    rowActions = rowActions,
                    menuEnabled = !selectionMode,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ConfettiOverlay(trigger = celebration.confettiTrigger)
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    val lastBulk by viewModel.lastBulkResult.collectAsStateWithLifecycle()
    val bulkLabel = lastBulk?.let {
        if (it.failed > 0) stringResource(R.string.bulk_result_failed, it.succeeded, it.failed)
        else stringResource(R.string.bulk_result, it.succeeded)
    }
    LaunchedEffect(lastBulk) {
        if (lastBulk == null || bulkLabel == null) return@LaunchedEffect
        snackbarHost.showSnackbar(bulkLabel)
        viewModel.clearBulkResult()
    }
    UndoDeleteEffect(actions, snackbarHost)
    if (editorDraft != null) {
        TaskEditorSheet(
            viewModel = editorViewModel,
            reminderSettings = container.notificationSettingsStore,
            onDismiss = {},
        )
    }
    if (paletteVisible) {
        val allTasks by editorViewModel.allTasks.collectAsStateWithLifecycle()
        val views by container.smartViewStore.observeViews()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val themePref by container.prefs.theme.collectAsStateWithLifecycle(
            initialValue = dev.vinny.gsd.prefs.ThemePref.SYSTEM,
        )
        dev.vinny.gsd.palette.CommandPalette(
            tasks = allTasks,
            views = views,
            onOpenTask = editorViewModel::openExisting,
            onOpenView = { onNavigate("browse/view/$it") },
            onAction = { action ->
                when (action) {
                    dev.vinny.gsd.palette.PaletteAction.NEW_TASK -> editorViewModel.openNew(null)
                    dev.vinny.gsd.palette.PaletteAction.TOGGLE_SHOW_COMPLETED ->
                        scope.launch { container.prefs.setShowCompleted(!showCompleted) }
                    dev.vinny.gsd.palette.PaletteAction.TOGGLE_THEME -> scope.launch {
                        container.prefs.setTheme(
                            when (themePref) {
                                dev.vinny.gsd.prefs.ThemePref.SYSTEM -> dev.vinny.gsd.prefs.ThemePref.LIGHT
                                dev.vinny.gsd.prefs.ThemePref.LIGHT -> dev.vinny.gsd.prefs.ThemePref.DARK
                                dev.vinny.gsd.prefs.ThemePref.DARK -> dev.vinny.gsd.prefs.ThemePref.SYSTEM
                            },
                        )
                    }
                    dev.vinny.gsd.palette.PaletteAction.SEND_FEEDBACK -> onNavigate("feedback")
                }
            },
            onNavigate = { nav ->
                when (nav) {
                    dev.vinny.gsd.palette.PaletteNav.MATRIX -> onNavigate("matrix")
                    dev.vinny.gsd.palette.PaletteNav.DASHBOARD -> onNavigate("dashboard")
                    dev.vinny.gsd.palette.PaletteNav.ARCHIVE -> onNavigate("archive")
                    dev.vinny.gsd.palette.PaletteNav.SETTINGS -> onNavigate("settings")
                }
            },
            onDismiss = { paletteVisible = false },
        )
    }
}

/** Compact-width matrix: a vertical stack of quadrant sections (spec §6.1). */
@Composable
fun MatrixList(
    sections: List<QuadrantSectionState>?,
    onToggleComplete: (dev.vinny.gsd.model.Task) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (dev.vinny.gsd.model.Task) -> Unit = {},
    rowActions: ((dev.vinny.gsd.model.Task) -> TaskRowActions)? = null,
    menuEnabled: Boolean = true,
) {
    if (sections == null) {
        MatrixSkeleton(modifier = modifier)
        return
    }
    val allVisible = sections.flatMap { it.tasks }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        sections.forEach { section ->
            item(key = "header-${section.quadrant.id}") {
                QuadrantHeader(
                    section,
                    modifier = Modifier.padding(top = if (section.quadrant == Quadrant.DO_FIRST) 0.dp else 20.dp),
                )
            }
            if (section.tasks.isEmpty()) {
                item(key = "empty-${section.quadrant.id}") { EmptyQuadrantPrompt(section.quadrant) }
            } else {
                items(section.tasks, key = { it.id }) { task ->
                    val badges = TaskBadges(
                        blocked = dev.vinny.gsd.model.DependencyGraph.uncompletedBlockers(allVisible, task).size,
                        blocking = dev.vinny.gsd.model.DependencyGraph.blocking(allVisible, task).size,
                    )
                    SwipeTaskRow(
                        task = task,
                        onComplete = { onToggleComplete(task) },
                        onDelete = { onDelete(task) },
                    ) {
                        val actions = rowActions?.invoke(task) ?: TaskRowActions(
                            onEdit = { onOpen(task.id) },
                            onToggleComplete = { onToggleComplete(task) },
                            onDelete = { onDelete(task) },
                        )
                        TaskRowWithMenu(task = task, badges = badges, actions = actions, menuEnabled = menuEnabled)
                    }
                }
            }
        }
    }
}

private val quadrantIcons: Map<Quadrant, ImageVector> = mapOf(
    Quadrant.DO_FIRST to Icons.Outlined.LocalFireDepartment,
    Quadrant.SCHEDULE to Icons.Outlined.CalendarMonth,
    Quadrant.DELEGATE to Icons.Outlined.Group,
    Quadrant.ELIMINATE to Icons.Outlined.Delete,
)

@Composable
internal fun QuadrantHeader(section: QuadrantSectionState, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val accent = gsd.accent(section.quadrant)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Icon(quadrantIcons.getValue(section.quadrant), contentDescription = null, tint = accent)
        }
        Text(
            section.quadrant.title,
            style = GsdType.quadrantTitle,
            color = accent,
            modifier = Modifier.weight(1f),
        )
        Text(
            section.activeCount.toString(),
            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
            color = gsd.ink2,
        )
    }
}

private val emptyCopy: Map<Quadrant, Int> = mapOf(
    Quadrant.DO_FIRST to R.string.matrix_empty_q1,
    Quadrant.SCHEDULE to R.string.matrix_empty_q2,
    Quadrant.DELEGATE to R.string.matrix_empty_q3,
    Quadrant.ELIMINATE to R.string.matrix_empty_q4,
)

/** Dashed in-context empty prompt (spec §5.10): hairlineStrong dash 5, radius 22. */
@Composable
private fun EmptyQuadrantPrompt(quadrant: Quadrant) {
    val gsd = LocalGsdColors.current
    val strokeColor = gsd.hairlineStrong
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val dash = with(density) { 5.dp.toPx() }
                drawRoundRect(
                    color = strokeColor,
                    cornerRadius = CornerRadius(with(density) { 22.dp.toPx() }),
                    style = Stroke(
                        width = with(density) { 1.dp.toPx() },
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                    ),
                )
            }
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(emptyCopy.getValue(quadrant)), style = GsdType.subhead, color = gsd.ink2)
    }
}
