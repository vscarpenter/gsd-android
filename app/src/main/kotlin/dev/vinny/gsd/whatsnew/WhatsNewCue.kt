package dev.vinny.gsd.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.WhatsNew
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * One line under the capture bar after a notable update, and nothing else: no dialog, snackbar,
 * or badge (PRODUCT.md, never nag). The rule is [WhatsNew.decide], pinned by the shared corpus.
 * "See what's new", Dismiss, or opening What's New records the running version on this device.
 *
 * @param existingUser null while tasks load. A device with no tasks is a first run: it records the
 *   running version and never sees the line (new users get onboarding instead).
 */
@Composable
fun WhatsNewCue(
    container: AppContainer,
    existingUser: Boolean?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val releases by rememberAndroidReleases()
    // "" means never recorded; null means DataStore hasn't answered yet, so nothing is decided.
    val lastSeen by remember { container.prefs.whatsNewLastSeen.map { it.orEmpty() } }
        .collectAsStateWithLifecycle<String?>(initialValue = null)
    val scope = rememberCoroutineScope()
    val current = WhatsNewContent.runningVersion

    val loadedReleases = releases
    val seen = lastSeen
    val decision = if (loadedReleases == null || seen == null || existingUser == null) {
        null
    } else {
        WhatsNew.decide(
            releases = loadedReleases.map { WhatsNew.Entry(it.version, it.notable) },
            currentVersion = current,
            lastSeenVersion = seen.ifEmpty { null },
            existingUser = existingUser,
        )
    }
    val recordNow = decision?.recordVersion
    LaunchedEffect(recordNow) {
        if (recordNow != null) container.prefs.setWhatsNewLastSeen(recordNow)
    }

    val release = decision?.takeIf { it.show }
        ?.let { d -> loadedReleases?.firstOrNull { it.version == d.headlineVersion } }
        ?: return
    val headline = release.headline ?: return
    val markSeen: () -> Unit = { scope.launch { container.prefs.setWhatsNewLastSeen(current) } }

    val gsd = LocalGsdColors.current
    val prefix = stringResource(R.string.whats_new_cue_prefix, release.version)
    val dismissDescription = stringResource(R.string.whats_new_dismiss_description)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = gsd.ink)) { append(prefix) }
                append(" ")
                append(headline)
            },
            style = GsdType.metadata,
            color = gsd.ink2,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { markSeen(); onOpen() }) {
                Text(stringResource(R.string.whats_new_see), style = GsdType.metadata, color = gsd.tint)
            }
            TextButton(
                onClick = markSeen,
                modifier = Modifier.semantics { contentDescription = dismissDescription },
            ) {
                Text(stringResource(R.string.whats_new_dismiss), style = GsdType.metadata, color = gsd.ink2)
            }
        }
    }
}
