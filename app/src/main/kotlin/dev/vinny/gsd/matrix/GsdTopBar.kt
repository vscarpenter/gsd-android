package dev.vinny.gsd.matrix

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    syncChip: (@Composable () -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandMark()
        Text("GSD", style = GsdType.cardTitle.copy(fontWeight = FontWeight.Bold), color = gsd.ink)
        Text("·", style = GsdType.cardTitle, color = gsd.ink3)
        Text(
            screenName,
            style = GsdType.cardTitle.copy(fontWeight = FontWeight.SemiBold),
            color = gsd.ink2,
            modifier = Modifier.weight(1f),
        )
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
            IconButton(onClick = onToggleSelectMode) {
                Icon(
                    Icons.Outlined.Checklist,
                    contentDescription = stringResource(R.string.cd_select_mode),
                    tint = gsd.ink2,
                )
            }
        }
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
