package dev.vinny.gsd.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.BuildConfig
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.BrandMark
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.openInBrowser
import dev.vinny.gsd.ui.openSupportEmail
import kotlinx.coroutines.launch

/**
 * Settings' About group (spec §8.17 and §8.19): the mark and tagline, the version with its build,
 * the privacy blurb and policy, support by email, the Field Guide, onboarding again, the site, and
 * the maker's credit. It had only the version and three rows (review finding 20).
 */
@Composable
internal fun AboutGroup(container: AppContainer, onOpenHelp: () -> Unit) {
    val gsd = LocalGsdColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    GroupLabel(R.string.settings_about)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BrandMark(Modifier.size(56.dp))
        Text(
            stringResource(R.string.about_tagline),
            style = GsdType.quadrantTitle,
            color = gsd.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.about_summary),
            style = GsdType.subhead,
            color = gsd.ink2,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = GsdType.metadata,
            color = gsd.ink3,
        )
    }
    Text(stringResource(R.string.settings_privacy_blurb), style = GsdType.metadata, color = gsd.ink3)
    // Play's User Data policy wants the policy inside the app, since sync sends email and tasks (finding 15)
    ActionRow(R.string.settings_privacy_policy) { openInBrowser(context, AppContainer.PRIVACY_POLICY_URL) }
    ActionRow(R.string.settings_contact_support) { openSupportEmail(context, AppContainer.SUPPORT_EMAIL) }
    ActionRow(R.string.settings_field_guide, onClick = onOpenHelp)
    ActionRow(R.string.settings_show_onboarding) {
        scope.launch { container.prefs.setHasOnboarded(false) }
    }
    ActionRow(R.string.about_site) { openInBrowser(context, AppContainer.SITE_URL) }
    val credit = buildAnnotatedString {
        append(stringResource(R.string.about_credit_by))
        append(" ")
        withLink(LinkAnnotation.Url(AppContainer.MAKER_URL, TextLinkStyles(SpanStyle(color = gsd.tint)))) {
            append(MAKER_NAME)
        }
    }
    Text(credit, style = GsdType.metadata, color = gsd.ink3, modifier = Modifier.padding(top = 4.dp))
}

private const val MAKER_NAME = "Vinny Carpenter"
