package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

/** Placeholder — the real matrix (quadrant sections + cards) lands in plan Task 11. */
@Composable
fun MatrixScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Capture your first task", style = GsdType.quadrantTitle, color = gsd.ink)
    }
}
