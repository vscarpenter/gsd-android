package dev.vinny.gsd.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(viewModel: TaskEditorViewModel, onDismiss: () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val task = draft ?: return
    val gsd = LocalGsdColors.current
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(
        onDismissRequest = {
            viewModel.dismiss()
            onDismiss()
        },
        shape = GsdShapes.sheet,
        containerColor = gsd.surface,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            EditorContent(
                draft = task,
                canSave = task.title.isNotBlank(),
                onEdit = viewModel::edit,
                onSave = {
                    viewModel.save(onDone = onDismiss, onError = { error = it })
                },
            )
            error?.let {
                Text(
                    it,
                    style = GsdType.metadata,
                    color = gsd.alert,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** Stateless editor body — Phase 1 sections in spec §8.3 order. */
@Composable
fun EditorContent(
    draft: Task,
    canSave: Boolean,
    onEdit: ((Task) -> Task) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.editor_title_new), style = GsdType.title, color = gsd.ink)

        SectionLabel(R.string.editor_section_title)
        BasicTextField(
            value = draft.title,
            onValueChange = { new -> onEdit { it.copy(title = new) } },
            textStyle = GsdType.body.copy(color = gsd.ink),
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsdShapes.input)
                .background(gsd.sunken)
                .padding(12.dp)
                .testTag("editor_title"),
        )

        SectionLabel(R.string.editor_section_quadrant)
        QuadrantPicker(draft, onEdit)

        SectionLabel(R.string.editor_section_tags)
        TagsEditor(draft, onEdit)

        SectionLabel(R.string.editor_section_notes)
        BasicTextField(
            value = draft.description,
            onValueChange = { new -> onEdit { it.copy(description = new) } },
            textStyle = GsdType.body.copy(color = gsd.ink),
            modifier = Modifier
                .fillMaxWidth()
                .clip(GsdShapes.input)
                .background(gsd.sunken)
                .padding(12.dp)
                .testTag("editor_notes"),
        )

        val links = detectLinks(draft.description)
        if (links.isNotEmpty()) {
            SectionLabel(R.string.editor_section_links)
            links.forEach { link ->
                Text(link, style = GsdType.subhead, color = gsd.tint)
            }
        }

        TextButton(onClick = onSave, enabled = canSave, modifier = Modifier.align(Alignment.End)) {
            Text(
                stringResource(R.string.editor_save),
                style = GsdType.cardTitle,
                color = if (canSave) gsd.tint else gsd.ink3,
            )
        }
    }
}

internal fun detectLinks(notes: String): List<String> =
    Regex("""https?://\S+""").findAll(notes).map { it.value }.toList()

@Composable
private fun SectionLabel(labelRes: Int) {
    Text(
        stringResource(labelRes).uppercase(),
        style = GsdType.caption,
        color = LocalGsdColors.current.ink3,
    )
}

/** 2×2 picker mirroring the matrix (spec §8.3): selected = accent border + wash fill. */
@Composable
private fun QuadrantPicker(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    val rows = listOf(
        listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE),
        listOf(Quadrant.DELEGATE, Quadrant.ELIMINATE),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { quadrant ->
                    val selected = draft.quadrant == quadrant
                    val accent = gsd.accent(quadrant)
                    Text(
                        quadrant.title,
                        style = GsdType.subhead,
                        color = if (selected) accent else gsd.ink2,
                        modifier = Modifier
                            .weight(1f)
                            .clip(GsdShapes.small)
                            .background(if (selected) gsd.wash(quadrant) else Color.Transparent)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = accent.copy(alpha = if (selected) 1f else 0.35f),
                                shape = GsdShapes.small,
                            )
                            .clickable {
                                onEdit {
                                    it.copy(
                                        urgent = quadrant == Quadrant.DO_FIRST || quadrant == Quadrant.DELEGATE,
                                        important = quadrant == Quadrant.DO_FIRST || quadrant == Quadrant.SCHEDULE,
                                    )
                                }
                            }
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TagsEditor(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    var pending by remember { mutableStateOf("") }
    fun commit() {
        val normalized = TaskValidator.normalizeTags(draft.tags + pending)
        onEdit { it.copy(tags = normalized) }
        pending = ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (draft.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.tags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(gsd.wash(draft.quadrant))
                            .padding(start = 10.dp, end = 2.dp),
                    ) {
                        Text("#$tag", style = GsdType.metadata, color = gsd.accent(draft.quadrant))
                        IconButton(
                            onClick = { onEdit { it.copy(tags = it.tags - tag) } },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_remove_tag, tag),
                                tint = gsd.ink3,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        androidx.compose.foundation.layout.Box {
            if (pending.isEmpty()) {
                Text(stringResource(R.string.editor_tag_hint), style = GsdType.subhead, color = gsd.ink3)
            }
            BasicTextField(
                value = pending,
                onValueChange = { new ->
                    if (new.endsWith(",")) {
                        pending = new.dropLast(1)
                        commit()
                    } else {
                        pending = new
                    }
                },
                textStyle = GsdType.subhead.copy(color = gsd.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("editor_tag_input"),
            )
        }
    }
}
