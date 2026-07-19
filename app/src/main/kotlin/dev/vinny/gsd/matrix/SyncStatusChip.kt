package dev.vinny.gsd.matrix

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

data class SyncChipState(
    val signedIn: Boolean = false,
    val syncing: Boolean = false,
    val pending: Int = 0,
    val failed: Int = 0,
)

/** Quiet-until-it-matters (spec §5.10): hidden entirely when idle, healthy, and nothing pending. */
@Composable
fun SyncStatusChip(state: SyncChipState, modifier: Modifier = Modifier) {
    if (!state.signedIn) return
    if (!state.syncing && state.pending == 0 && state.failed == 0) return
    val gsd = LocalGsdColors.current
    val description = stringResource(R.string.cd_sync_status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        when {
            state.failed > 0 -> Icon(
                Icons.Outlined.SyncProblem,
                contentDescription = null,
                tint = gsd.ochre, // soft health warning: attention, not alarm (spec §5.3)
                modifier = Modifier.size(18.dp),
            )
            else -> {
                Icon(
                    Icons.Outlined.Sync,
                    contentDescription = null,
                    tint = gsd.ink2,
                    modifier = Modifier.size(18.dp),
                )
                if (!state.syncing && state.pending > 0) {
                    Text(
                        "${state.pending}",
                        style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
                        color = gsd.ink2,
                    )
                }
            }
        }
    }
}
