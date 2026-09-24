package dev.vinny.gsd.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.vinny.gsd.R
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.HintedField
import dev.vinny.gsd.ui.SectionHeading

/**
 * The §8.14 command palette: one query, sectioned results, substring matching. It opens at the top
 * of its list with the query focused and the keyboard up (review finding 26).
 */
@Composable
fun CommandPalette(
    tasks: List<Task>,
    views: List<SmartView>,
    onOpenTask: (String) -> Unit,
    onOpenView: (String) -> Unit,
    onAction: (PaletteAction) -> Unit,
    onNavigate: (PaletteNav) -> Unit,
    onDismiss: () -> Unit,
) {
    val gsd = LocalGsdColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    Dialog(onDismissRequest = onDismiss) {
        // Here, in the dialog's own composition, so the field's focus requester is attached first
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }
        // A new query starts at the top of its results
        LaunchedEffect(query) { listState.scrollToItem(0) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsdShapes.sheet)
                .background(gsd.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val hint = stringResource(R.string.palette_hint)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = GsdType.body.copy(color = gsd.ink),
                cursorBrush = gsd.cursor,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("palette_query"),
                decorationBox = { inner -> HintedField(hint, query.isEmpty(), GsdType.body, inner) },
            )
            val actionLabels = PaletteAction.entries.associateWith { stringResource(it.labelRes) }
            val navLabels = PaletteNav.entries.associateWith { stringResource(it.labelRes) }
            val actionLabel = { action: PaletteAction -> actionLabels.getValue(action) }
            val navLabel = { nav: PaletteNav -> navLabels.getValue(nav) }
            val results = PaletteIndex.query(query, tasks, views, actionLabel, navLabel)
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                section(R.string.palette_tasks, results.tasks, { it.title }) { onOpenTask(it.id); onDismiss() }
                section(R.string.palette_views, results.views, { it.name }) { onOpenView(it.id); onDismiss() }
                section(R.string.palette_actions, results.actions, actionLabel) { onAction(it); onDismiss() }
                section(R.string.palette_navigation, results.navigation, navLabel) { onNavigate(it); onDismiss() }
            }
        }
    }
}

/** The words each command shows, and matches on (review, Copy: they were English literals). */
private val PaletteAction.labelRes: Int
    get() = when (this) {
        PaletteAction.NEW_TASK -> R.string.palette_new_task
        PaletteAction.TOGGLE_SHOW_COMPLETED -> R.string.palette_toggle_completed
        PaletteAction.TOGGLE_THEME -> R.string.palette_toggle_theme
        PaletteAction.SEND_FEEDBACK -> R.string.palette_send_feedback
    }

private val PaletteNav.labelRes: Int
    get() = when (this) {
        PaletteNav.MATRIX -> R.string.nav_matrix
        PaletteNav.DASHBOARD -> R.string.nav_dashboard
        PaletteNav.ARCHIVE -> R.string.browse_archive
        PaletteNav.SETTINGS -> R.string.nav_settings
    }

private fun <T> androidx.compose.foundation.lazy.LazyListScope.section(
    labelRes: Int,
    items: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    if (items.isEmpty()) return
    // No key: the smart views load a frame late, and a keyed header held the scroll where the first
    // section had been, so the palette opened scrolled past them (finding 26). By index, the top stays.
    item {
        SectionHeading(stringResource(labelRes), Modifier.padding(top = 8.dp))
    }
    items.forEach { entry ->
        item {
            val gsd = LocalGsdColors.current
            Text(
                label(entry),
                style = GsdType.body,
                color = gsd.ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSelect(entry) }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
