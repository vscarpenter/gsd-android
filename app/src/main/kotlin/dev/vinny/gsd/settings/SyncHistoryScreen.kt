package dev.vinny.gsd.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.ui.reorderMotion
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Totals + the ~50 most recent attempts (spec §8.17.3 / §9.7). */
@Composable
fun SyncHistoryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val entries by container.db.syncHistoryDao().observeRecent(50)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val format = DateTimeFormatter.ofPattern("MMM d, HH:mm:ss")
    Column(modifier = modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (entries.isEmpty()) {
            // The §5.10 empty state (finding 32)
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty),
                body = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries, key = { it.id }) { entry ->
                    Row(modifier = reorderMotion(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            format.format(Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())),
                            style = GsdType.metadata,
                            color = gsd.ink2,
                        )
                        Text(
                            entry.status,
                            style = GsdType.metadata,
                            color = when (entry.status) {
                                "error" -> gsd.alert
                                "partial" -> gsd.ochre
                                else -> gsd.success
                            },
                        )
                        Text(
                            "↑${entry.pushedCount} ↓${entry.pulledCount}" +
                                if (entry.conflictsResolved > 0) " ⚡${entry.conflictsResolved}" else "",
                            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
                            color = gsd.ink3,
                            modifier = Modifier.weight(1f),
                        )
                        Text("${entry.duration ?: 0}ms", style = GsdType.metadata, color = gsd.ink3)
                    }
                }
            }
        }
    }
}
