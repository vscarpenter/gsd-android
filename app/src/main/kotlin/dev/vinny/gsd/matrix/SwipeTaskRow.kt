package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Where a row can rest: at rest, swiped through to complete or to its end, or open on its buttons. */
private enum class SwipeAnchor { Settled, Completed, Revealed, Through }

/** Each revealed trailing button's width. */
private val ActionWidth = 76.dp

/** The trailing swipe's snooze: one hour, as on iOS (spec §8.1). */
val QuickSnooze: java.time.Duration = java.time.Duration.ofHours(1)

/**
 * Row gestures (spec §8.1). A leading full swipe completes. With [onSnooze], a trailing swipe opens
 * two buttons, Delete and Snooze, and a full trailing swipe snoozes for an hour, as on iOS. Without
 * it (Trash), a full trailing swipe runs [onDelete], the one trailing action. A full swipe runs its
 * action once, when the swipe settles, and the row slides back. The background is drawn only while
 * the row is moved. Gestures are never the only path: the disc and menu cover the same actions (§13.3).
 */
@Composable
fun SwipeTaskRow(
    task: Task,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSnooze: (() -> Unit)? = null,
    onMarkUrgent: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val gsd = LocalGsdColors.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val state = remember { AnchoredDraggableState(initialValue = SwipeAnchor.Settled) }
    val complete by rememberUpdatedState(onComplete)
    val delete by rememberUpdatedState(onDelete)
    val snooze by rememberUpdatedState(onSnooze)
    val twoButtons = onSnooze != null
    // An escalating card gets a third button beside Snooze (spec-escalation.md §5)
    val markUrgent by rememberUpdatedState(onMarkUrgent)
    val escalation = rememberEscalation(task)
    val showMarkUrgent = twoButtons && onMarkUrgent != null && escalation != null
    val buttonCount = if (showMarkUrgent) 3 else 2
    var width by remember { mutableFloatStateOf(0f) }

    // One action per settle, then back to rest (review finding 3: a drag used to fire it repeatedly)
    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { settled ->
            when (settled) {
                SwipeAnchor.Completed -> complete()
                SwipeAnchor.Through -> snooze?.invoke() ?: delete()
                SwipeAnchor.Settled, SwipeAnchor.Revealed -> return@collect
            }
            try {
                state.animateTo(SwipeAnchor.Settled)
            } catch (interrupted: CancellationException) {
                // A new drag took the row over. Keep listening, unless this effect itself is ending.
                currentCoroutineContext().ensureActive()
            }
        }
    }
    fun close(then: () -> Unit) {
        scope.launch { state.animateTo(SwipeAnchor.Settled) }
        then()
    }

    // Re-anchored when the width or the button count changes, so a card that starts or stops escalating
    // opens to the right distance
    val reveal = with(density) { (ActionWidth * buttonCount).toPx() }
    LaunchedEffect(width, reveal, twoButtons) {
        if (width == 0f) return@LaunchedEffect
        state.updateAnchors(
            DraggableAnchors {
                SwipeAnchor.Settled at 0f
                SwipeAnchor.Completed at width
                if (twoButtons) SwipeAnchor.Revealed at -reveal
                SwipeAnchor.Through at -width
            },
        )
    }

    Box(
        modifier = modifier.onSizeChanged { size -> width = size.width.toFloat() },
    ) {
        val offset = if (state.offset.isNaN()) 0f else state.offset
        // A row at rest paints nothing behind its content (review finding 2)
        if (offset > 0f) {
            Box(
                modifier = Modifier.matchParentSize().clip(GsdShapes.card).background(gsd.success),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = gsd.inkOnAccent,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        } else if (offset < 0f) {
            Row(modifier = Modifier.matchParentSize().clip(GsdShapes.card), horizontalArrangement = Arrangement.End) {
                val exposed = with(density) { (-offset).toDp() }
                if (twoButtons) {
                    TrailingAction(
                        label = stringResource(R.string.menu_delete),
                        icon = Icons.Outlined.Delete,
                        color = gsd.alert,
                        width = exposed / buttonCount,
                    ) { close(delete) }
                    TrailingAction(
                        label = stringResource(R.string.menu_snooze),
                        icon = Icons.Outlined.Bedtime,
                        color = gsd.slate,
                        width = exposed / buttonCount,
                    ) { close { snooze?.invoke() } }
                    if (showMarkUrgent) {
                        TrailingAction(
                            label = stringResource(R.string.menu_mark_urgent),
                            icon = Icons.Outlined.ArrowUpward,
                            color = escalation?.target?.quadrant?.let(gsd::accentInk) ?: gsd.rust,
                            width = exposed / buttonCount,
                        ) { close { markUrgent?.invoke() } }
                    }
                } else {
                    TrailingAction(
                        label = stringResource(R.string.menu_delete),
                        icon = Icons.Outlined.Delete,
                        color = gsd.alert,
                        width = exposed,
                    ) { close(delete) }
                }
            }
        }
        Box(
            modifier = Modifier
                .offset { IntOffset((if (rtl) -offset else offset).roundToInt(), 0) }
                .anchoredDraggable(
                    state = state,
                    reverseDirection = rtl,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                ),
        ) {
            content()
        }
    }
}

/** A revealed button behind the row: an icon over its label, on its color, the width it has so far. */
@Composable
private fun TrailingAction(
    label: String,
    icon: ImageVector,
    color: Color,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(color)
            .clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = gsd.inkOnAccent, modifier = Modifier.size(20.dp))
        Text(label, style = GsdType.metadata, color = gsd.inkOnAccent, maxLines = 1)
    }
}
