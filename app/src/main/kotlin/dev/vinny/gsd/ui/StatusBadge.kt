package dev.vinny.gsd.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

enum class BadgeStyle { ALERT, NEUTRAL }

/** A short state label on a wash capsule; only overdue earns the alert pigment (S§2.3). */
@Composable
fun StatusBadge(text: String, style: BadgeStyle, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val (ink, wash) = when (style) {
        BadgeStyle.ALERT -> gsd.alert to gsd.alertWash
        BadgeStyle.NEUTRAL -> gsd.ink2 to gsd.sunken
    }
    Text(
        text,
        style = GsdType.caption.copy(fontWeight = FontWeight.SemiBold),
        color = ink,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(wash).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
