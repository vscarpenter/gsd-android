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
import android.view.HapticFeedbackConstants
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.withTimeoutOrNull
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
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

@Composable
fun MatrixScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val viewModel: MatrixViewModel = viewModel { MatrixViewModel(container.store, container.prefs) }
    val captureViewModel: CaptureViewModel = viewModel { CaptureViewModel(container.store) }
    val editorViewModel: TaskEditorViewModel = viewModel { TaskEditorViewModel(container.store) }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val captureState by captureViewModel.state.collectAsStateWithLifecycle()
    val editorDraft by editorViewModel.draft.collectAsStateWithLifecycle()
    var confettiTrigger by remember { mutableIntStateOf(0) }
    val snackbarHost = remember { SnackbarHostState() }
    val view = LocalView.current
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            CaptureBarContent(
                state = captureState,
                onTextChange = captureViewModel::onTextChange,
                onCycleOverride = captureViewModel::cycleOverride,
                onSubmit = { captureViewModel.submit(onError = {}) },
                onDetails = { editorViewModel.openNew(captureViewModel.draftForEditor()) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            MatrixList(
                sections = sections,
                onToggleComplete = { task ->
                    viewModel.toggleCompleted(task.id)
                    if (!task.completed) {
                        // The one haptic in the app (spec §5.8) + the one celebration.
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        confettiTrigger++
                    }
                },
                onOpen = editorViewModel::openExisting,
                onDelete = { viewModel.delete(it.id) },
                modifier = Modifier.weight(1f),
            )
        }
        ConfettiOverlay(trigger = confettiTrigger)
        SnackbarHost(
            hostState = snackbarHost,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    val lastDeleted by viewModel.lastDeleted.collectAsStateWithLifecycle()
    val deletedLabel = lastDeleted?.let { stringResource(R.string.snackbar_deleted, it.title) }
    val undoLabel = stringResource(R.string.snackbar_undo)
    val accessibilityOn = remember(view) {
        (view.context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager)?.isEnabled == true
    }
    LaunchedEffect(lastDeleted) {
        if (lastDeleted == null || deletedLabel == null) return@LaunchedEffect
        // ~6 s to undo, extended to ~12 s under TalkBack (spec §8.20)
        val result = withTimeoutOrNull(if (accessibilityOn) 12_000L else 6_000L) {
            snackbarHost.showSnackbar(message = deletedLabel, actionLabel = undoLabel)
        }
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete()
        } else {
            snackbarHost.currentSnackbarData?.dismiss()
            viewModel.clearUndo()
        }
    }
    if (editorDraft != null) {
        TaskEditorSheet(viewModel = editorViewModel, onDismiss = {})
    }
}

/** Compact-width matrix: a vertical stack of quadrant sections (spec §6.1). */
@Composable
fun MatrixList(
    sections: List<QuadrantSectionState>,
    onToggleComplete: (dev.vinny.gsd.model.Task) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (dev.vinny.gsd.model.Task) -> Unit = {},
) {
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
                    SwipeTaskRow(
                        task = task,
                        onComplete = { onToggleComplete(task) },
                        onDelete = { onDelete(task) },
                    ) {
                        TaskCard(
                            task = task,
                            onToggleComplete = { onToggleComplete(task) },
                            onClick = { onOpen(task.id) },
                        )
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
private fun QuadrantHeader(section: QuadrantSectionState, modifier: Modifier = Modifier) {
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
