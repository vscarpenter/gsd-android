package dev.vinny.gsd.matrix

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.SavedPlacement
import dev.vinny.gsd.model.ShareText
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TodayView
import dev.vinny.gsd.model.TopThree
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Duration
import java.time.ZoneId

data class TaskBadges(val blocked: Int, val blocking: Int)

/**
 * The card menu's top three rows (spec-top-three.md §4). [action] says which of Add, Remove, or the
 * disabled "Today is full" shows. The move rows appear only for one of today's picks.
 */
data class TodayRowMenu(
    val action: TodayView.MenuAction = TodayView.MenuAction.NONE,
    val canMoveUp: Boolean = false,
    val canMoveDown: Boolean = false,
    val onAdd: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onMoveUp: () -> Unit = {},
    val onMoveDown: () -> Unit = {},
)

/** Callbacks for the §8.1 long-press context menu. Defaults are no-ops for tests. */
data class TaskRowActions(
    val onEdit: () -> Unit = {},
    val onToggleComplete: () -> Unit = {},
    val onToggleTimer: () -> Unit = {},
    val onSnooze: (Duration) -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onMoveTo: (Quadrant) -> Unit = {},
    val onSave: () -> Unit = {},
    val onMarkUrgent: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val today: TodayRowMenu = TodayRowMenu(),
)

/** The Today rows of [task]'s menu for [view], wired to the four writes. A null view, before the first snapshot, offers none. */
fun todayRowMenu(
    view: TodayView?,
    task: Task,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onMove: (TopThree.Move) -> Unit,
): TodayRowMenu {
    if (view == null) return TodayRowMenu()
    return TodayRowMenu(
        action = view.menuActionFor(task),
        canMoveUp = view.canMoveUp(task.id),
        canMoveDown = view.canMoveDown(task.id),
        onAdd = onAdd,
        onRemove = onRemove,
        onMoveUp = { onMove(TopThree.Move.UP) },
        onMoveDown = { onMove(TopThree.Move.DOWN) },
    )
}

/** The full §8.1 menu for one task. Every screen that draws a task row builds its actions here. */
fun taskRowActions(
    task: Task,
    context: Context,
    actions: TaskActionsViewModel,
    celebration: CompletionCelebration,
    onEdit: () -> Unit,
    today: TodayRowMenu = TodayRowMenu(),
): TaskRowActions = TaskRowActions(
    onEdit = onEdit,
    onToggleComplete = {
        celebration.onToggle(task)
        actions.toggleCompleted(task.id)
    },
    onToggleTimer = { actions.toggleTimer(task.id) },
    onSnooze = { duration -> actions.snooze(task.id, duration) },
    onDuplicate = { actions.duplicate(task.id) },
    onShare = {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, ShareText.render(task, ZoneId.systemDefault()))
        }
        context.startActivity(Intent.createChooser(send, null))
    },
    onMoveTo = { quadrant -> actions.moveToQuadrant(task.id, quadrant) },
    onSave = { actions.moveToSaved(task.id) },
    onMarkUrgent = { actions.markUrgent(task.id) },
    onDelete = { actions.delete(task.id) },
    today = today,
)

private val menuSnoozePresets: List<Pair<Duration, Int>> = listOf(
    Duration.ofMinutes(15) to R.string.snooze_15m,
    Duration.ofMinutes(30) to R.string.snooze_30m,
    Duration.ofHours(1) to R.string.snooze_1h,
    Duration.ofHours(3) to R.string.snooze_3h,
    Duration.ofDays(1) to R.string.snooze_tomorrow,
    Duration.ofDays(7) to R.string.snooze_next_week,
)

/** A task card with the §8.1 menu. [selected] is null outside select mode; in it the card has no menu. */
@Composable
fun TaskRowWithMenu(
    task: Task,
    badges: TaskBadges,
    actions: TaskRowActions,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    longPressOpensMenu: Boolean = true,
    /** "first", "second", or "third" for a row in the Today section, read after the title. */
    positionLabel: String? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var snoozeOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    val escalation = rememberEscalation(task)
    val markUrgentLabel = escalation?.let { stringResource(R.string.action_mark_urgent_moves_to, it.target?.quadrant?.title.orEmpty()) }
    // TalkBack's actions for the card (spec §13.3), so the swipes and the menu are not the only way
    val completeLabel = stringResource(if (task.completed) R.string.menu_uncomplete else R.string.menu_complete)
    val editLabel = stringResource(R.string.menu_edit)
    val snoozeLabel = stringResource(R.string.action_snooze_hour)
    val deleteLabel = stringResource(R.string.menu_delete)
    val addToTodayLabel = stringResource(R.string.today_add)
    val removeFromTodayLabel = stringResource(R.string.today_remove)
    val today = actions.today
    val cardActions = if (selected == null) {
        Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(completeLabel) { actions.onToggleComplete(); true },
                CustomAccessibilityAction(editLabel) { actions.onEdit(); true },
                CustomAccessibilityAction(snoozeLabel) { actions.onSnooze(QuickSnooze); true },
            ) + listOfNotNull(
                when (today.action) {
                    TodayView.MenuAction.ADD -> CustomAccessibilityAction(addToTodayLabel) { today.onAdd(); true }
                    TodayView.MenuAction.REMOVE -> CustomAccessibilityAction(removeFromTodayLabel) { today.onRemove(); true }
                    else -> null
                },
            ) + listOfNotNull(
                if (markUrgentLabel != null) CustomAccessibilityAction(markUrgentLabel) { actions.onMarkUrgent(); true } else null,
            ) + listOf(
                CustomAccessibilityAction(deleteLabel) { actions.onDelete(); true },
            )
        }
    } else {
        Modifier
    }
    Box(modifier = modifier) {
        TaskCard(
            task = task,
            onToggleComplete = actions.onToggleComplete,
            onClick = actions.onEdit,
            modifier = cardActions,
            badges = badges,
            // Select mode turns the menu off, and a card with no menu draws no button.
            onOpenMenu = if (selected == null) { { menuOpen = true } } else null,
            longPressOpensMenu = longPressOpensMenu,
            selected = selected,
            escalation = escalation,
            positionLabel = positionLabel,
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(R.string.menu_edit) { menuOpen = false; actions.onEdit() }
            MenuItem(if (task.completed) R.string.menu_uncomplete else R.string.menu_complete) {
                menuOpen = false; actions.onToggleComplete()
            }
            TodayMenuRows(today) { menuOpen = false }
            MenuItem(
                if (TimeTracking.runningEntry(task) != null) R.string.menu_stop_timer
                else R.string.menu_start_timer,
            ) { menuOpen = false; actions.onToggleTimer() }
            MenuItem(R.string.menu_snooze, submenu = true) { snoozeOpen = true }
            if (escalation != null) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(stringResource(R.string.menu_mark_urgent), style = GsdType.body)
                            Text(escalationMovesTo(escalation), style = GsdType.metadata, color = LocalGsdColors.current.ink3)
                        }
                    },
                    onClick = { menuOpen = false; actions.onMarkUrgent() },
                    modifier = Modifier.testTag("menu_mark_urgent"),
                )
            }
            MenuItem(R.string.menu_duplicate) { menuOpen = false; actions.onDuplicate() }
            MenuItem(R.string.menu_share) { menuOpen = false; actions.onShare() }
            MenuItem(R.string.menu_move_to, submenu = true) { moveOpen = true }
            // Not for a completed task or a running timer (web ADR 0018): the row offers nothing it would refuse
            if (SavedPlacement.canSave(task)) {
                MenuItem(R.string.menu_move_to_saved) { menuOpen = false; actions.onSave() }
            }
            MenuItem(R.string.menu_delete, color = LocalGsdColors.current.alert) {
                menuOpen = false; actions.onDelete()
            }
        }
        DropdownMenu(expanded = snoozeOpen, onDismissRequest = { snoozeOpen = false }) {
            menuSnoozePresets.forEach { (duration, labelRes) ->
                MenuItem(labelRes) {
                    snoozeOpen = false; menuOpen = false
                    actions.onSnooze(duration)
                }
            }
        }
        DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
            Quadrant.entries.forEach { quadrant ->
                DropdownMenuItem(
                    text = { Text(quadrant.title, style = GsdType.body) },
                    onClick = {
                        moveOpen = false; menuOpen = false
                        actions.onMoveTo(quadrant)
                    },
                )
            }
        }
    }
}

/** One menu row. Delete reads in the alert color, and a chevron marks a row that opens a submenu (finding 31). */
@Composable
private fun MenuItem(labelRes: Int, color: Color = Color.Unspecified, submenu: Boolean = false, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), style = GsdType.body, color = color) },
        onClick = onClick,
        trailingIcon = if (submenu) {
            {
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = LocalGsdColors.current.ink3,
                    modifier = Modifier.testTag("submenu_cue"),
                )
            }
        } else {
            null
        },
    )
}

/** Add to today, Remove from today, or a disabled "Today is full", then Move up and Move down for a pick. */
@Composable
private fun TodayMenuRows(today: TodayRowMenu, close: () -> Unit) {
    when (today.action) {
        TodayView.MenuAction.ADD -> MenuItem(R.string.today_add) { close(); today.onAdd() }
        TodayView.MenuAction.REMOVE -> {
            MenuItem(R.string.today_remove) { close(); today.onRemove() }
            if (today.canMoveUp) MenuItem(R.string.today_move_up) { close(); today.onMoveUp() }
            if (today.canMoveDown) MenuItem(R.string.today_move_down) { close(); today.onMoveDown() }
        }
        TodayView.MenuAction.FULL -> DropdownMenuItem(
            text = { Text(stringResource(R.string.today_full), style = GsdType.body) },
            onClick = {},
            enabled = false,
        )
        TodayView.MenuAction.NONE -> Unit
    }
}
