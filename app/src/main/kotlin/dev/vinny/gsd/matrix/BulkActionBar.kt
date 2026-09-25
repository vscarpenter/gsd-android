package dev.vinny.gsd.matrix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.DuePreset
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.HintedField
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bottom bulk bar (spec §8.11): complete, move, tags, due date, delete (confirmed). It shows as soon
 * as select mode starts, and until something is selected it says how to select.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BulkActionBar(
    selectedCount: Int,
    selectionTags: List<String>,
    onRun: (BulkOp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    var moveOpen by remember { mutableStateOf(false) }
    var dueOpen by remember { mutableStateOf(false) }
    var removeTagOpen by remember { mutableStateOf(false) }
    var addTagOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(gsd.surface2)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (selectedCount == 0) {
            // Select mode is on and nothing is picked yet, so the bar says how to pick (finding 13)
            Text(stringResource(R.string.bulk_select_prompt), style = GsdType.metadata, color = gsd.ink2)
        } else {
            Text(
                stringResource(R.string.bulk_selected, selectedCount),
                style = GsdType.metadata,
                color = gsd.ink2,
            )
            BarButton(R.string.bulk_complete) { onRun(BulkOp.Complete) }
            Box {
                BarButton(R.string.bulk_move) { moveOpen = true }
                DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                    Quadrant.entries.forEach { quadrant ->
                        DropdownMenuItem(
                            text = { Text(quadrant.title, style = GsdType.body) },
                            onClick = { moveOpen = false; onRun(BulkOp.MoveTo(quadrant)) },
                        )
                    }
                }
            }
            Box {
                BarButton(R.string.bulk_tags) { addTagOpen = true }
            }
            Box {
                BarButton(R.string.bulk_due) { dueOpen = true }
                DropdownMenu(expanded = dueOpen, onDismissRequest = { dueOpen = false }) {
                    listOf(
                        DuePreset.TODAY to R.string.due_today,
                        DuePreset.THIS_WEEK to R.string.due_this_week,
                        DuePreset.NEXT_WEEK to R.string.due_next_week,
                        DuePreset.NONE to R.string.due_none,
                    ).forEach { (preset, labelRes) ->
                        DropdownMenuItem(
                            text = { Text(stringResource(labelRes), style = GsdType.body) },
                            onClick = {
                                dueOpen = false
                                val due = preset.resolve(LocalDate.now(zone))?.atStartOfDay(zone)?.toInstant()
                                onRun(BulkOp.SetDue(due))
                            },
                        )
                    }
                }
            }
            BarButton(R.string.bulk_delete, tint = gsd.alert) { confirmDelete = true }
        }
    }

    if (addTagOpen) {
        var tag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addTagOpen = false },
            title = { Text(stringResource(R.string.bulk_tags), style = GsdType.cardTitle) },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val hint = stringResource(R.string.bulk_add_tag)
                    BasicTextField(
                        value = tag,
                        onValueChange = { tag = it },
                        textStyle = GsdType.body.copy(color = gsd.ink),
                        cursorBrush = gsd.cursor,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("bulk_tag_input"),
                        decorationBox = { inner -> HintedField(hint, tag.isEmpty(), GsdType.body, inner) },
                    )
                    if (selectionTags.isNotEmpty()) {
                        Text(stringResource(R.string.bulk_remove_tag_hint), style = GsdType.metadata, color = gsd.ink3)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectionTags.take(5).forEach { existing ->
                                TextButton(onClick = {
                                    addTagOpen = false
                                    onRun(BulkOp.RemoveTag(existing))
                                }) { Text("−$existing", style = GsdType.metadata, color = gsd.alert) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        addTagOpen = false
                        if (tag.isNotBlank()) onRun(BulkOp.AddTag(tag.trim()))
                    },
                ) { Text(stringResource(R.string.bulk_add_tag)) }
            },
            dismissButton = {
                TextButton(onClick = { addTagOpen = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    pluralStringResource(R.plurals.bulk_delete_confirm, selectedCount, selectedCount),
                    style = GsdType.cardTitle,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onRun(BulkOp.Delete) }) {
                    Text(stringResource(R.string.bulk_delete), color = gsd.alert)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun BarButton(labelRes: Int, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    TextButton(onClick = onClick) {
        Text(stringResource(labelRes), style = GsdType.metadata, color = tint ?: gsd.tint)
    }
}
