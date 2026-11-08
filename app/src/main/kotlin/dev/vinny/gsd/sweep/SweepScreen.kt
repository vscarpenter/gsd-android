package dev.vinny.gsd.sweep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.browse.ReturnDialog
import dev.vinny.gsd.editor.DueDateSection
import dev.vinny.gsd.matrix.ScreenTopBar
import dev.vinny.gsd.matrix.TaskActionsViewModel
import dev.vinny.gsd.matrix.TaskCard
import dev.vinny.gsd.matrix.UndoDeleteEffect
import dev.vinny.gsd.matrix.UndoSaveEffect
import dev.vinny.gsd.matrix.UndoUrgentEffect
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.SweepAction
import dev.vinny.gsd.model.SweepReason
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The weekly sweep (spec-weekly-sweep.md §7): one card at a time, a position line with no bar, and an end
 * that says "That's the sweep." and offers a way back. It states facts and asks nothing of the reader.
 */
@Composable
fun SweepScreen(
    container: AppContainer,
    onUp: () -> Unit,
    onBackToMatrix: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SweepViewModel = viewModel { SweepViewModel(container.store, container.prefs) }
    val actions: TaskActionsViewModel = viewModel { TaskActionsViewModel(container.store, container.trashStore) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val offerTopThree by viewModel.offerTopThree.collectAsStateWithLifecycle(initialValue = false)
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) { viewModel.failures.collect { snackbar.showSnackbar(context.getString(it.message)) } }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ScreenTopBar(stringResource(R.string.sweep_title), onUp = onUp)
            when (val current = state) {
                SweepState.Loading -> Unit
                is SweepState.Card -> SweepCardPage(
                    card = current,
                    onAction = { action -> run(action, current.task, viewModel, actions) },
                    onReturn = viewModel::returnToMatrix,
                    onSetDue = viewModel::setDue,
                    onMove = { quadrant -> viewModel.advanceAfter { actions.moveToQuadrantNow(current.task.id, quadrant) } },
                    onDone = { viewModel.done(onBackToMatrix) },
                )
                SweepState.End -> SweepEnd(
                    offerTopThree = offerTopThree,
                    onPick = {
                        // The Matrix opens the Today suggestions once it's showing again
                        container.palette.requestTodaySuggestions()
                        onBackToMatrix()
                    },
                    onBack = onBackToMatrix,
                )
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).imePadding())
    }
    UndoDeleteEffect(actions, snackbar)
    UndoSaveEffect(actions, snackbar)
    UndoUrgentEffect(actions, snackbar)
}

/**
 * Actions that open a dialog are handled by the page. The rest run here, and the sweep moves on only once
 * the write worked (spec decision S12), so a failed Mark urgent or Delete leaves the card in place.
 */
private fun run(action: SweepAction, task: Task, viewModel: SweepViewModel, actions: TaskActionsViewModel) {
    when (action) {
        SweepAction.COMPLETE -> viewModel.complete(task.id)
        SweepAction.MARK_URGENT -> viewModel.advanceAfter { actions.markUrgentNow(task.id) }
        SweepAction.SAVE_FOR_LATER -> viewModel.advanceAfter { actions.moveToSavedNow(task.id) }
        SweepAction.DELETE -> viewModel.advanceAfter { actions.deleteNow(task.id) }
        SweepAction.SKIP -> viewModel.skip()
        SweepAction.NEW_DATE, SweepAction.ADD_DATE, SweepAction.MOVE_TO_QUADRANT, SweepAction.RETURN_TO_MATRIX -> Unit
    }
}

private enum class Dialog { DATE, MOVE, RETURN }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SweepCardPage(
    card: SweepState.Card,
    onAction: (SweepAction) -> Unit,
    onReturn: (String, Boolean, Boolean, Instant?) -> Unit,
    onSetDue: (String, Instant) -> Unit,
    onMove: (Quadrant) -> Unit,
    onDone: () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val task = card.task
    var dialog by remember(task.id) { mutableStateOf<Dialog?>(null) }
    val focus = remember { FocusRequester() }
    // Focus moves to the new card after each action, so TalkBack lands on it (spec §7)
    LaunchedEffect(task.id) { focus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.sweep_position, card.position, card.total),
            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
            color = gsd.ink2,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("sweep_position"),
        )
        Box(modifier = Modifier.focusRequester(focus).focusable()) {
            TaskCard(task = task, onToggleComplete = null, onClick = {})
        }
        // Out of its quadrant's column, the card's accent alone would say where it is (spec §7: the card shows it)
        Text(
            task.quadrant.title,
            style = GsdType.metadata,
            color = gsd.accentInk(task.quadrant),
            modifier = Modifier.testTag("sweep_quadrant"),
        )
        Text(
            stringResource(reasonLine(card.reason)),
            style = GsdType.subhead,
            color = gsd.ink2,
            modifier = Modifier.testTag("sweep_reason"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SweepAction.forCard(card.reason, task).forEach { action ->
                TextButton(
                    onClick = {
                        when (action) {
                            SweepAction.NEW_DATE, SweepAction.ADD_DATE -> dialog = Dialog.DATE
                            SweepAction.MOVE_TO_QUADRANT -> dialog = Dialog.MOVE
                            SweepAction.RETURN_TO_MATRIX -> dialog = Dialog.RETURN
                            else -> onAction(action)
                        }
                    },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sweep_${action.name.lowercase()}"),
                ) {
                    Text(
                        stringResource(actionLabel(action)),
                        style = GsdType.body,
                        color = when (action) {
                            SweepAction.DELETE -> gsd.alert
                            SweepAction.SKIP -> gsd.ink2
                            else -> gsd.tint
                        },
                    )
                }
            }
        }
        // Always available (spec §4): stamps last swept and leaves
        TextButton(onClick = onDone, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sweep_done")) {
            Text(stringResource(R.string.sweep_done), style = GsdType.body, color = gsd.ink2)
        }
    }

    val zone = ZoneId.systemDefault()
    when (dialog) {
        Dialog.DATE -> NewDateDialog(
            task = task,
            onSave = { due -> dialog = null; onSetDue(task.id, due) },
            onDismiss = { dialog = null },
        )
        Dialog.MOVE -> MoveDialog(
            current = task.quadrant,
            onPick = { quadrant -> dialog = null; onMove(quadrant) },
            onDismiss = { dialog = null },
        )
        Dialog.RETURN -> ReturnDialog(
            task = task,
            today = LocalDate.now(zone),
            zone = zone,
            onConfirm = { quadrant, due -> dialog = null; onReturn(task.id, quadrant.urgent, quadrant.important, due) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
internal fun SweepEnd(offerTopThree: Boolean, onPick: () -> Unit, onBack: () -> Unit) {
    val gsd = LocalGsdColors.current
    val heading = remember { FocusRequester() }
    // At the end, focus moves to the heading, as it does to each new card (spec decision S8)
    LaunchedEffect(Unit) { heading.requestFocus() }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.sweep_end_title),
            style = GsdType.quadrantTitle,
            color = gsd.ink,
            modifier = Modifier.focusRequester(heading).focusable().semantics { heading() }.testTag("sweep_end"),
        )
        // Only while today has a free slot. It writes nothing: the Today section's "Add to today" does.
        if (offerTopThree) {
            Button(onClick = onPick, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sweep_pick_top_three")) {
                Text(stringResource(R.string.sweep_end_pick), style = GsdType.body)
            }
        }
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = 48.dp).testTag("sweep_back")) {
            Text(stringResource(R.string.sweep_end_back), style = GsdType.body, color = gsd.tint)
        }
    }
}

/**
 * New date and Add date use the editor's own date control (spec decision S10): the date picker, All day or a
 * time of day, and the presets. The result is one ordinary edit, written when the user saves. There's no None,
 * so Save waits for a date: clearing one stays the editor's job (sweep N3).
 */
@Composable
private fun NewDateDialog(task: Task, onSave: (Instant) -> Unit, onDismiss: () -> Unit) {
    var draft by remember(task.id) { mutableStateOf(task) }
    val due = draft.dueDate
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sweep_date_title), style = GsdType.cardTitle) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DueDateSection(draft = draft, onEdit = { change -> draft = change(draft) }, offerNone = false)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { due?.let(onSave) },
                enabled = due != null,
                modifier = Modifier.testTag("sweep_date_save"),
            ) {
                Text(stringResource(R.string.sweep_date_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
    )
}

@Composable
private fun MoveDialog(current: Quadrant, onPick: (Quadrant) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sweep_move_title), style = GsdType.cardTitle) },
        text = {
            Column {
                // Not the quadrant it's in already (spec decision S11)
                (Quadrant.entries - current).forEach { quadrant ->
                    ChoiceRow(quadrant.title, selected = false) { onPick(quadrant) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_cancel)) } },
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

private fun reasonLine(reason: SweepReason): Int = when (reason) {
    SweepReason.OVERDUE -> R.string.sweep_reason_overdue
    SweepReason.ESCALATING -> R.string.sweep_reason_escalating
    SweepReason.STALE -> R.string.sweep_reason_stale
    SweepReason.SAVED -> R.string.sweep_reason_saved
    SweepReason.UNDATED_SCHEDULE -> R.string.sweep_reason_undated_schedule
}

private fun actionLabel(action: SweepAction): Int = when (action) {
    SweepAction.COMPLETE -> R.string.sweep_action_complete
    SweepAction.NEW_DATE -> R.string.sweep_action_new_date
    SweepAction.ADD_DATE -> R.string.sweep_action_add_date
    SweepAction.MARK_URGENT -> R.string.sweep_action_mark_urgent
    SweepAction.MOVE_TO_QUADRANT -> R.string.sweep_action_move
    SweepAction.SAVE_FOR_LATER -> R.string.sweep_action_save
    SweepAction.RETURN_TO_MATRIX -> R.string.sweep_action_return
    SweepAction.DELETE -> R.string.sweep_action_delete
    SweepAction.SKIP -> R.string.sweep_action_skip
}
