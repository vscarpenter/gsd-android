package dev.vinny.gsd.onboarding

import android.animation.ValueAnimator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vinny.gsd.R
import dev.vinny.gsd.matrix.BrandMark
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 4

/**
 * First-run onboarding (spec §8.19): four skippable, paged screens on paper, each a hero, a serif
 * title, and one sentence, as on iOS. Skip sits fixed at the top and the ink pill at the bottom, so
 * neither moves between pages, and the page survives rotation (review finding 19). The last page
 * offers sync sign-in through [onSignIn], which gets a provider id.
 */
@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onSignIn: ((String) -> Unit)? = null,
) {
    val gsd = LocalGsdColors.current
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    Box(
        modifier = modifier.fillMaxSize().background(gsd.paper).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A column capped for tablets: uncapped, the hero floats small and the pill stretches
        Column(modifier = Modifier.widthIn(max = 560.dp).fillMaxHeight()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onFinished, modifier = Modifier.padding(8.dp)) {
                    Text(stringResource(R.string.onboarding_skip), style = GsdType.subhead, color = gsd.ink3)
                }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page -> Page(page) }
            PageDots(
                current = pager.currentPage,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 14.dp),
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp, end = 28.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pager.currentPage < PAGE_COUNT - 1) {
                    InkPill(stringResource(R.string.onboarding_next)) {
                        scope.launch {
                            val next = pager.currentPage + 1
                            // No slide under reduced motion (spec §13.3); a swipe still follows the finger
                            if (ValueAnimator.areAnimatorsEnabled()) pager.animateScrollToPage(next) else pager.scrollToPage(next)
                        }
                    }
                } else {
                    InkPill(stringResource(R.string.onboarding_start), onFinished)
                    if (onSignIn != null) {
                        SignInPill(stringResource(R.string.sign_in_google)) { onSignIn("google") }
                        SignInPill(stringResource(R.string.sign_in_apple)) { onSignIn("apple") }
                        Text(
                            stringResource(R.string.onboarding_sync_hint),
                            style = GsdType.metadata,
                            color = gsd.ink3,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Page(page: Int) {
    when (page) {
        0 -> PageLayout(R.string.onboarding_welcome_title, R.string.onboarding_welcome_body) {
            BrandMark(Modifier.size(92.dp))
        }
        1 -> PageLayout(R.string.onboarding_matrix_title, R.string.onboarding_matrix_body) { AxesDiagram() }
        2 -> PageLayout(R.string.onboarding_capture_title, R.string.onboarding_capture_body) { CaptureLegend() }
        else -> PageLayout(R.string.onboarding_privacy_title, R.string.onboarding_privacy_body) { LockMotif() }
    }
}

/** Hero, serif title, and one sentence, centered, and scrolling when a large font outgrows the page. */
@Composable
private fun PageLayout(title: Int, body: Int, hero: @Composable () -> Unit) {
    val gsd = LocalGsdColors.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 36.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.padding(bottom = 6.dp)) { hero() }
            Text(
                stringResource(title),
                style = GsdType.title,
                color = gsd.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(body),
                style = GsdType.body,
                color = gsd.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
    }
}

/** A 2×2 of quadrant-wash tiles with the two axes labeled. Decorative: the sentence says it. */
@Composable
private fun AxesDiagram() {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.widthIn(max = 268.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.onboarding_axis_important),
            style = GsdType.caption.copy(fontWeight = FontWeight.SemiBold),
            color = gsd.ink3,
            maxLines = 1,
            modifier = Modifier.turnedUp(),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisTile(Quadrant.DO_FIRST, Modifier.weight(1f))
                AxisTile(Quadrant.SCHEDULE, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisTile(Quadrant.DELEGATE, Modifier.weight(1f))
                AxisTile(Quadrant.ELIMINATE, Modifier.weight(1f))
            }
            Text(
                stringResource(R.string.onboarding_axis_urgent),
                style = GsdType.caption.copy(fontWeight = FontWeight.SemiBold),
                color = gsd.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AxisTile(quadrant: Quadrant, modifier: Modifier) {
    val gsd = LocalGsdColors.current
    Text(
        quadrant.title,
        style = GsdType.quadrantTitle.copy(fontSize = 15.sp),
        color = gsd.accent(quadrant),
        modifier = modifier
            .heightIn(min = 62.dp)
            .clip(GsdShapes.small)
            .background(gsd.wash(quadrant))
            .padding(11.dp),
    )
}

/** Lays a one-line label on its side, reading bottom to top, and takes the turned size. */
private fun Modifier.turnedUp(): Modifier = this
    .layout { measurable, _ ->
        val placeable = measurable.measure(Constraints())
        layout(placeable.height, placeable.width) {
            placeable.place(
                x = -(placeable.width / 2 - placeable.height / 2),
                y = -(placeable.height / 2 - placeable.width / 2),
            )
        }
    }
    .rotate(-90f)

/** A sample capture line, then a legend of the four tokens. The legend rows read to TalkBack. */
@Composable
private fun CaptureLegend() {
    val gsd = LocalGsdColors.current
    val accent = gsd.accent(Quadrant.DO_FIRST)
    val mono = FontFamily.Monospace
    Column(modifier = Modifier.widthIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(gsd.surface)
                .border(1.dp, gsd.hairline, CircleShape)
                .padding(horizontal = 18.dp, vertical = 13.dp)
                .clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.onboarding_capture_sample) + " ", style = GsdType.body, color = gsd.ink)
            Text("!!", style = GsdType.body.copy(fontFamily = mono, fontWeight = FontWeight.SemiBold), color = accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "#family",
                style = GsdType.metadata.copy(fontFamily = mono, fontWeight = FontWeight.SemiBold),
                color = accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(gsd.wash(Quadrant.DO_FIRST))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LegendRow("!!", stringResource(R.string.onboarding_legend_both))
            LegendRow("!", stringResource(R.string.onboarding_legend_urgent))
            LegendRow("*", stringResource(R.string.onboarding_legend_important))
            LegendRow("#tag", stringResource(R.string.onboarding_legend_tag))
        }
    }
}

@Composable
private fun LegendRow(token: String, meaning: String) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            token,
            style = GsdType.subhead.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold),
            color = gsd.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 46.dp)
                .clip(GsdShapes.tile)
                .background(gsd.sunken)
                .padding(horizontal = 6.dp, vertical = 6.dp),
        )
        Text(meaning, style = GsdType.subhead, color = gsd.ink2)
    }
}

@Composable
private fun LockMotif() {
    val gsd = LocalGsdColors.current
    Box(
        modifier = Modifier.size(92.dp).clip(CircleShape).background(gsd.sunken),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = gsd.ink2, modifier = Modifier.size(38.dp))
    }
}

/** Where the reader is: the current page is a long ink capsule. TalkBack hears "Page 2 of 4". */
@Composable
private fun PageDots(current: Int, modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val position = stringResource(R.string.onboarding_page, current + 1, PAGE_COUNT)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = position },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        repeat(PAGE_COUNT) { index ->
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (index == current) 22.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (index == current) gsd.ink else gsd.hairlineStrong),
            )
        }
    }
}

/** The one primary action: an ink-filled pill (spec §8.19). */
@Composable
private fun InkPill(label: String, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = gsd.ink, contentColor = gsd.paper),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    ) {
        Text(label, style = GsdType.body.copy(fontWeight = FontWeight.SemiBold))
    }
}

/** Optional sync sign-in: the tint is kept for it, on a quiet outlined pill. */
@Composable
private fun SignInPill(label: String, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, gsd.hairlineStrong),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(label, style = GsdType.body, color = gsd.tint)
    }
}
