package dev.vinny.gsd.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

/**
 * A sync sign-in choice, in Settings and on onboarding's last page: a quiet outlined pill with Tide
 * text, at least 48dp tall. Settings showed bare text links (review finding 29).
 */
@Composable
fun SignInButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, gsd.hairlineStrong),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label, style = GsdType.body, color = gsd.tint)
    }
}
