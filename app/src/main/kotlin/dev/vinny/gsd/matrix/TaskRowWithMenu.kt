package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TimeTracking
import dev.vinny.gsd.theme.GsdType
import java.time.Duration

data class TaskBadges(val blocked: Int, val blocking: Int)

/** Callbacks for the §8.1 long-press context menu. Defaults are no-ops for tests. */
data class TaskRowActions(
    val onEdit: () -> Unit = {},
    val onToggleComplete: () -> Unit = {},
    val onToggleTimer: () -> Unit = {},
    val onSnooze: (Duration) -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onMoveTo: (Quadrant) -> Unit = {},
    val onDelete: () -> Unit = {},
)

private val menuSnoozePresets: List<Pair<Duration, Int>> = listOf(
    Duration.ofMinutes(15) to R.string.snooze_15m,
    Duration.ofMinutes(30) to R.string.snooze_30m,
    Duration.ofHours(1) to R.string.snooze_1h,
    Duration.ofHours(3) to R.string.snooze_3h,
    Duration.ofDays(1) to R.string.snooze_tomorrow,
    Duration.ofDays(7) to R.string.snooze_next_week,
)

@Composable
fun TaskRowWithMenu(
    task: Task,
    badges: TaskBadges,
    actions: TaskRowActions,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var snoozeOpen by remember { mutableStateOf(false) }
    var moveOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        TaskCard(
            task = task,
            onToggleComplete = actions.onToggleComplete,
            onClick = actions.onEdit,
            badges = badges,
            onOpenMenu = { menuOpen = true },
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(R.string.menu_edit) { menuOpen = false; actions.onEdit() }
            MenuItem(if (task.completed) R.string.menu_uncomplete else R.string.menu_complete) {
                menuOpen = false; actions.onToggleComplete()
            }
            MenuItem(
                if (TimeTracking.runningEntry(task) != null) R.string.menu_stop_timer
                else R.string.menu_start_timer,
            ) { menuOpen = false; actions.onToggleTimer() }
            MenuItem(R.string.menu_snooze) { snoozeOpen = true }
            MenuItem(R.string.menu_duplicate) { menuOpen = false; actions.onDuplicate() }
            MenuItem(R.string.menu_share) { menuOpen = false; actions.onShare() }
            MenuItem(R.string.menu_move_to) { moveOpen = true }
            MenuItem(R.string.menu_delete) { menuOpen = false; actions.onDelete() }
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

@Composable
private fun MenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(labelRes), style = GsdType.body) },
        onClick = onClick,
    )
}
