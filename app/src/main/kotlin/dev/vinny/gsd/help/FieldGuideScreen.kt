package dev.vinny.gsd.help

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.openInBrowser

/**
 * The Field Guide (spec §8.19): a static reference, scrolling, in the order of the shared Field Guide
 * outline (GSD Project doc parity/docs-review-2026-11-02.md §3.3). It was one paragraph with no scroll,
 * and it still said sync was coming (review finding 20). Keyboard shortcuts show only where a hardware
 * keyboard is likely.
 */
@Composable
fun FieldGuideScreen(modifier: Modifier = Modifier) {
    val gsd = LocalGsdColors.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val keyboardLikely = configuration.keyboard == Configuration.KEYBOARD_QWERTY || configuration.screenWidthDp >= 600
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 44.dp),
            verticalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.guide_title),
                    style = GsdType.largeTitle,
                    color = gsd.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(gsd.hairline))
            }
            Section(R.string.guide_board, Icons.Outlined.GridView) {
                RuledItem(R.string.guide_board_matrix_title, R.string.guide_board_matrix_body)
                RuledItem(R.string.guide_board_capture_title, R.string.guide_board_capture_body)
            }
            Section(R.string.guide_quadrants, Icons.Outlined.ViewModule, carded = false) {
                QuadrantRow(Quadrant.DO_FIRST, R.string.guide_q1)
                QuadrantRow(Quadrant.SCHEDULE, R.string.guide_q2)
                QuadrantRow(Quadrant.DELEGATE, R.string.guide_q3)
                QuadrantRow(Quadrant.ELIMINATE, R.string.guide_q4)
            }
            Section(R.string.guide_syntax, Icons.Outlined.TextFields) {
                Text(stringResource(R.string.guide_syntax_body), style = GsdType.subhead, color = gsd.ink2)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TokenRow("!", R.string.guide_syntax_urgent)
                    TokenRow("!!", R.string.guide_syntax_both)
                    TokenRow("*", R.string.guide_syntax_important)
                    TokenRow("#tag", R.string.guide_syntax_tag)
                    TokenRow("https://…", R.string.guide_syntax_link)
                }
                // Spec 8.19's example, verbatim
                Text(
                    "!! ship the deck #work #q2",
                    style = GsdType.subhead.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                    color = gsd.ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GsdShapes.small)
                        .background(gsd.sunken)
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
                Text(stringResource(R.string.guide_syntax_example_result), style = GsdType.metadata, color = gsd.ink3)
            }
            Section(R.string.guide_due, Icons.Outlined.CalendarMonth) {
                RuledItem(R.string.guide_due_dates_title, R.string.guide_due_dates_body)
                RuledItem(R.string.guide_reminders_title, R.string.guide_reminders_body)
                RuledItem(R.string.guide_escalation_title, R.string.guide_escalation_body)
            }
            Section(R.string.guide_gestures, Icons.Outlined.TouchApp) {
                RuledItem(R.string.guide_complete_title, R.string.guide_complete_body)
                RuledItem(R.string.guide_edit_title, R.string.guide_edit_body)
                RuledItem(R.string.guide_more_title, R.string.guide_more_body)
                RuledItem(R.string.guide_drag_title, R.string.guide_drag_body)
            }
            Section(R.string.guide_keep, Icons.Outlined.BookmarkBorder) {
                RuledItem(R.string.guide_saved_title, R.string.guide_saved_body)
                RuledItem(R.string.guide_trash_title, R.string.guide_trash_body)
            }
            Section(R.string.guide_find, Icons.Outlined.Search) {
                RuledItem(R.string.guide_search_title, R.string.guide_search_body)
                RuledItem(R.string.guide_views_title, R.string.guide_views_body)
                RuledItem(R.string.guide_select_title, R.string.guide_select_body)
            }
            Section(R.string.guide_sync, Icons.Outlined.Sync) {
                Text(stringResource(R.string.guide_sync_body), style = GsdType.subhead, color = gsd.ink2)
                Text(stringResource(R.string.guide_sync_chip), style = GsdType.subhead, color = gsd.ink2)
            }
            if (keyboardLikely) {
                Section(R.string.guide_keys, Icons.Outlined.Keyboard) {
                    KeysRow(listOf("Ctrl", "K"), R.string.guide_keys_palette)
                    KeysRow(listOf("Ctrl", "F"), R.string.guide_keys_search)
                    KeysRow(listOf("Ctrl", "N"), R.string.guide_keys_new)
                    KeysRow(listOf("Ctrl", "1–4"), R.string.guide_keys_quadrant)
                    KeysRow(listOf("Esc"), R.string.guide_keys_close)
                }
            }
            Section(R.string.guide_privacy, Icons.Outlined.Lock) {
                Text(stringResource(R.string.guide_privacy_body), style = GsdType.subhead, color = gsd.ink2)
            }
            TextButton(onClick = { openInBrowser(context, AppContainer.SITE_URL) }) {
                Text(stringResource(R.string.guide_site), style = GsdType.subhead, color = gsd.tint)
            }
        }
    }
}

/** A quiet icon and an uppercase label over the section, its content on a card unless [carded] is off. */
@Composable
private fun Section(label: Int, icon: ImageVector, carded: Boolean = true, content: @Composable () -> Unit) {
    val gsd = LocalGsdColors.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(icon, contentDescription = null, tint = gsd.ink2, modifier = Modifier.size(16.dp))
            Text(
                stringResource(label).uppercase(),
                style = GsdType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
                color = gsd.ink3,
            )
        }
        val body = if (carded) {
            Modifier
                .fillMaxWidth()
                .clip(GsdShapes.card)
                .background(gsd.surface)
                .border(1.dp, gsd.hairline, GsdShapes.card)
                .padding(16.dp)
        } else {
            Modifier.fillMaxWidth()
        }
        Column(modifier = body, verticalArrangement = Arrangement.spacedBy(if (carded) 14.dp else 10.dp)) { content() }
    }
}

/** A serif title and a sentence behind a thin ink rule. */
@Composable
private fun RuledItem(title: Int, body: Int) {
    val gsd = LocalGsdColors.current
    Row(modifier = Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(gsd.ink))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = GsdType.quadrantTitle, color = gsd.ink)
            Text(stringResource(body), style = GsdType.subhead, color = gsd.ink2)
        }
    }
}

@Composable
private fun QuadrantRow(quadrant: Quadrant, blurb: Int) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsdShapes.small)
            .background(gsd.wash(quadrant))
            .padding(horizontal = 13.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(gsd.accent(quadrant)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(quadrant.title, style = GsdType.quadrantTitle.copy(fontSize = 17.sp), color = gsd.ink)
            Text(stringResource(blurb), style = GsdType.subhead, color = gsd.ink2)
        }
    }
}

@Composable
private fun TokenRow(token: String, meaning: Int) {
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
        Text(stringResource(meaning), style = GsdType.subhead, color = gsd.ink2)
    }
}

/** Key caps, then what the keys do. The caps wrap at large font sizes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeysRow(keys: List<String>, label: Int) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FlowRow(modifier = Modifier.widthIn(min = 96.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            keys.forEach { key ->
                Text(
                    key,
                    style = GsdType.metadata.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
                    color = gsd.ink2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = 26.dp)
                        .clip(GsdShapes.tile)
                        .background(gsd.sunken)
                        .border(1.dp, gsd.hairline, GsdShapes.tile)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Text(stringResource(label), style = GsdType.subhead, color = gsd.ink2)
    }
}
