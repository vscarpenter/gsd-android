package dev.vinny.gsd.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.dashboard.StatCard
import dev.vinny.gsd.store.SyncHistoryEntity
import dev.vinny.gsd.store.SyncHistoryTotals
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.EmptyState
import dev.vinny.gsd.ui.formatDateTimeToSecond
import dev.vinny.gsd.ui.reorderMotion
import java.time.Instant
import java.time.ZoneId

/** Totals + the ~50 most recent attempts (spec §8.17.3 / §9.7). */
@Composable
fun SyncHistoryScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val dao = container.db.syncHistoryDao()
    val entries by dao.observeRecent(50).collectAsStateWithLifecycle(initialValue = emptyList())
    val totals by dao.observeTotals().collectAsStateWithLifecycle(initialValue = SyncHistoryTotals())
    SyncHistoryContent(entries, totals, modifier)
}

@Composable
internal fun SyncHistoryContent(
    entries: List<SyncHistoryEntity>,
    totals: SyncHistoryTotals,
    modifier: Modifier = Modifier,
) {
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
                item(key = "totals") { SyncHistoryTotalsGrid(totals, Modifier.padding(bottom = 10.dp)) }
                items(entries, key = { it.id }) { entry ->
                    SyncHistoryRow(entry, modifier = reorderMotion())
                }
            }
        }
    }
}

@Composable
private fun SyncHistoryTotalsGrid(totals: SyncHistoryTotals, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.history_total_syncs), "${totals.totalSyncs}", Modifier.weight(1f))
            StatCard(stringResource(R.string.history_total_successful), "${totals.successes}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(stringResource(R.string.history_total_sent), "${totals.totalPushed}", Modifier.weight(1f))
            StatCard(stringResource(R.string.history_total_received), "${totals.totalPulled}", Modifier.weight(1f))
        }
    }
}

/**
 * One attempt: when, how it went, what moved, and how long it took. The status is a word, not the
 * engine's "partial"; the time follows the device's clock and locale; and the arrows are read as
 * words, since TalkBack read them as symbols (review, Copy and Accessibility).
 */
@Composable
internal fun SyncHistoryRow(entry: SyncHistoryEntity, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val context = LocalContext.current
    val counts = buildString {
        append(stringResource(R.string.history_counts_a11y, entry.pushedCount, entry.pulledCount))
        if (entry.conflictsResolved > 0) {
            append(", ")
            append(pluralStringResource(R.plurals.history_conflicts_a11y, entry.conflictsResolved, entry.conflictsResolved))
        }
    }
    Row(modifier = modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            formatDateTimeToSecond(context, Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault())),
            style = GsdType.metadata,
            color = gsd.ink2,
        )
        Text(
            stringResource(
                when (entry.status) {
                    "error" -> R.string.history_status_error
                    "partial" -> R.string.history_status_partial
                    else -> R.string.history_status_success
                },
            ),
            style = GsdType.metadata,
            color = when (entry.status) {
                "error" -> gsd.alert
                "partial" -> gsd.ochreInk
                else -> gsd.success
            },
        )
        Text(
            "↑${entry.pushedCount} ↓${entry.pulledCount}" +
                if (entry.conflictsResolved > 0) " ⚡${entry.conflictsResolved}" else "",
            style = GsdType.metadata.copy(fontFeatureSettings = "tnum"),
            color = gsd.ink3,
            modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = counts },
        )
        Text(stringResource(R.string.history_duration, entry.duration ?: 0), style = GsdType.metadata, color = gsd.ink3)
    }
}
