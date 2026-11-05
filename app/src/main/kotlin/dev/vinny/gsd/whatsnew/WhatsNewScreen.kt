package dev.vinny.gsd.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.WhatsNewRelease
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.SectionHeading
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * What's New (gsd-taskmanager tasks/spec-whats-new.md): this build's Android release notes, newest
 * first, grouped New, Improved, Fixed. Opened from Settings > About, the palette, and the matrix
 * line. Opening it counts as reading it, so the matrix stops offering the line.
 */
@Composable
fun WhatsNewScreen(container: AppContainer, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val releases by rememberAndroidReleases()
    LaunchedEffect(Unit) { container.prefs.setWhatsNewLastSeen(WhatsNewContent.runningVersion) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val loaded = releases ?: return@LazyColumn
        if (loaded.isEmpty()) {
            item { Text(stringResource(R.string.whats_new_empty), style = GsdType.body, color = gsd.ink2) }
        }
        items(loaded, key = { it.version }) { release -> ReleaseNotes(release) }
    }
}

@Composable
private fun ReleaseNotes(release: WhatsNewRelease) {
    val gsd = LocalGsdColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                stringResource(R.string.whats_new_version, release.version),
                style = GsdType.quadrantTitle,
                color = gsd.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(displayDate(release.date), style = GsdType.metadata, color = gsd.ink3)
        }
        release.headline?.let { Text(it, style = GsdType.subhead, color = gsd.ink) }
        NotesGroup(R.string.whats_new_group_new, release.new)
        NotesGroup(R.string.whats_new_group_improved, release.improved)
        NotesGroup(R.string.whats_new_group_fixed, release.fixed)
        HorizontalDivider(color = gsd.ink3.copy(alpha = DIVIDER_ALPHA))
    }
}

@Composable
private fun NotesGroup(labelRes: Int, items: List<String>) {
    if (items.isEmpty()) return
    val gsd = LocalGsdColors.current
    SectionHeading(stringResource(labelRes))
    items.forEach { item ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("•", style = GsdType.subhead, color = gsd.ink3)
            Text(item, style = GsdType.subhead, color = gsd.ink)
        }
    }
}

private const val DIVIDER_ALPHA = 0.3f

/** A YYYY-MM-DD release date in the device's long date format. */
private fun displayDate(isoDate: String): String =
    runCatching { LocalDate.parse(isoDate).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)) }
        .getOrDefault(isoDate)
