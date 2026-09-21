package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.LocalGsdColors
import kotlinx.coroutines.launch

/**
 * Row gestures (spec §8.1): leading full-swipe completes; trailing reveals
 * delete (snooze joins in Phase 2). Gestures are never the only path —
 * the disc and menu cover the same actions for accessibility (§13.3).
 */
@Composable
fun SwipeTaskRow(
    task: Task,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        // Material asks confirmValueChange on every drag frame, so an action there ran eight or nine
        // times per swipe. onDismiss runs once, when the swipe settles.
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> onComplete()
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() } // slide back; the row re-renders in its new state
        },
        backgroundContent = {
            // A row at rest reveals nothing. Painting here shows through any content that is not opaque.
            val (color, icon, alignment) = when (state.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd ->
                    Triple(gsd.success, Icons.Outlined.Check, Alignment.CenterStart)
                SwipeToDismissBoxValue.EndToStart ->
                    Triple(gsd.alert, Icons.Outlined.Delete, Alignment.CenterEnd)
                SwipeToDismissBoxValue.Settled -> return@SwipeToDismissBox
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(GsdShapes.card)
                    .background(color),
                contentAlignment = alignment,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = gsd.inkOnAccent,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        },
        content = { content() },
    )
}
