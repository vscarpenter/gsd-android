package dev.vinny.gsd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

/**
 * The §5.10 empty state: a 60dp `sunken` tile holding a 28dp icon, a serif headline, and one `ink2`
 * sentence at most 280dp wide. The icon is `ink3` unless [iconTint] reassures, like the green check
 * for "Nothing overdue". TalkBack reads the whole state as one node, as iOS does.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconTint: Color? = null,
) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(60.dp).clip(GsdShapes.input).background(gsd.sunken),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint ?: gsd.ink3, modifier = Modifier.size(28.dp))
        }
        Text(title, style = GsdType.quadrantTitle, color = gsd.ink, textAlign = TextAlign.Center)
        Text(
            body,
            style = GsdType.subhead,
            color = gsd.ink2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 280.dp),
        )
    }
}
