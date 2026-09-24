package dev.vinny.gsd.matrix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

/** Brand lockup + actions (spec §5.4): mark · GSD · screen name; search + eye toggle. */
@Composable
fun GsdTopBar(
    screenName: String,
    showCompleted: Boolean,
    onToggleShowCompleted: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleSelectMode: (() -> Unit)? = null,
    selectMode: Boolean = false,
    syncChip: (@Composable () -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandMark()
        BrandLockup(screenName, modifier = Modifier.weight(1f))
        syncChip?.invoke()
        IconButton(onClick = onSearch) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = stringResource(R.string.cd_search),
                tint = gsd.ink2,
            )
        }
        IconButton(onClick = onToggleShowCompleted) {
            Icon(
                if (showCompleted) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                contentDescription = stringResource(
                    if (showCompleted) R.string.cd_hide_completed else R.string.cd_show_completed,
                ),
                tint = gsd.ink2,
            )
        }
        if (onToggleSelectMode != null) {
            // Checked while select mode is on, so the mode shows before anything is selected (finding 13)
            IconToggleButton(
                checked = selectMode,
                onCheckedChange = { onToggleSelectMode() },
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContainerColor = gsd.tideWash),
            ) {
                Icon(
                    Icons.Outlined.Checklist,
                    contentDescription = stringResource(R.string.cd_select_mode),
                    tint = if (selectMode) gsd.tint else gsd.ink2,
                )
            }
        }
    }
}

/**
 * The top bar of every screen but Matrix (spec §5.4, review finding 21): the brand lockup with the
 * screen's name, and the screen's own [actions]. A pushed screen passes [onUp] and gets an Up arrow
 * in place of the mark, so it no longer starts cold under the status bar with no way back but Back.
 */
@Composable
fun ScreenTopBar(
    screenName: String,
    modifier: Modifier = Modifier,
    onUp: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = if (onUp != null) 8.dp else 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onUp != null) {
            IconButton(onClick = onUp) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.cd_navigate_up),
                    tint = gsd.ink2,
                )
            }
        } else {
            BrandMark()
        }
        BrandLockup(screenName, modifier = Modifier.weight(1f))
        actions()
    }
}

/** "GSD · screen" (spec §5.4). TalkBack reads it as one heading and skips the dot. */
@Composable
private fun BrandLockup(screenName: String, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("GSD", style = GsdType.cardTitle.copy(fontWeight = FontWeight.Bold), color = gsd.ink)
        Text("·", style = GsdType.cardTitle, color = gsd.ink3, modifier = Modifier.clearAndSetSemantics {})
        Text(
            screenName,
            style = GsdType.cardTitle.copy(fontWeight = FontWeight.SemiBold),
            color = gsd.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The 24dp four-tile mark with a check on the Rust tile (spec §5.9), drawn inline. */
@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val tiles = listOf(gsd.rust, gsd.tide, gsd.ochre, gsd.slate)
    Canvas(modifier = modifier.size(24.dp)) {
        val gutter = size.width * 0.08f
        val tile = (size.width - gutter) / 2f
        val radius = CornerRadius(tile * 0.23f)
        val origins = listOf(
            Offset(0f, 0f), Offset(tile + gutter, 0f),
            Offset(0f, tile + gutter), Offset(tile + gutter, tile + gutter),
        )
        origins.forEachIndexed { index, origin ->
            drawRoundRect(color = tiles[index], topLeft = origin, size = Size(tile, tile), cornerRadius = radius)
        }
        // Check on the Rust (top-left) tile: a rising two-segment stroke
        val stroke = tile * 0.14f
        val checkColor = gsd.inkOnAccent
        drawLine(checkColor, Offset(tile * 0.25f, tile * 0.55f), Offset(tile * 0.45f, tile * 0.75f), stroke)
        drawLine(checkColor, Offset(tile * 0.45f, tile * 0.75f), Offset(tile * 0.78f, tile * 0.28f), stroke)
    }
}
