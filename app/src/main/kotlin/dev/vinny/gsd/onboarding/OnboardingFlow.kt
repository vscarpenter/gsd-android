package dev.vinny.gsd.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

private data class OnboardingPage(val title: Int, val body: Int, val monospaceBody: Boolean = false)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body),
    OnboardingPage(R.string.onboarding_matrix_title, R.string.onboarding_matrix_body),
    OnboardingPage(R.string.onboarding_capture_title, R.string.onboarding_capture_body, monospaceBody = true),
    OnboardingPage(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body),
)

/** Four skippable editorial pages on paper (spec §8.19). */
@Composable
fun OnboardingFlow(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    var page by remember { mutableIntStateOf(0) }
    val current = pages[page]
    val isLast = page == pages.lastIndex
    Column(
        modifier = modifier.fillMaxSize().background(gsd.paper).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(current.title), style = GsdType.title, color = gsd.ink)
        Text(
            stringResource(current.body),
            style = if (current.monospaceBody) GsdType.body.copy(fontFamily = FontFamily.Monospace) else GsdType.body,
            color = gsd.ink2,
            modifier = Modifier.widthIn(max = 280.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!isLast) {
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.onboarding_skip), style = GsdType.body, color = gsd.ink3)
                }
                TextButton(onClick = { page++ }) {
                    Text(stringResource(R.string.onboarding_next), style = GsdType.body, color = gsd.tint)
                }
            } else {
                TextButton(onClick = onFinished) {
                    Text(stringResource(R.string.onboarding_start), style = GsdType.cardTitle, color = gsd.tint)
                }
            }
        }
    }
}
