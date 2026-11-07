package dev.vinny.gsd.editor

import dev.vinny.gsd.matrix.escalationMovesTo
import dev.vinny.gsd.matrix.rememberEscalation
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.ReminderSettings
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.notifications.rememberReminderPermissionAsk
import dev.vinny.gsd.store.NotificationSettingsStore
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.HintedField
import dev.vinny.gsd.ui.SectionHeading
import dev.vinny.gsd.ui.announced
import dev.vinny.gsd.ui.validationMessage
import kotlinx.coroutines.launch

/**
 * The editor sheet (spec §6.3, §8.3). It opens fully expanded, with Cancel, the title, and Save
 * pinned above the scrolling sections. Save used to be a quiet button at the end of an
 * eleven-section scroll, hidden in the half-open sheet, and a swipe down threw edits away (review
 * finding 25). Now every way out (Cancel, Back, a swipe down, a tap outside) asks first when the
 * draft has unsaved changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(
    viewModel: TaskEditorViewModel,
    reminderSettings: NotificationSettingsStore,
    onDismiss: () -> Unit,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val askForReminders = rememberReminderPermissionAsk(reminderSettings)
    // A task with no offset shows and saves this device's default (D-10)
    val reminderDefaults by reminderSettings.observe().collectAsStateWithLifecycle(initialValue = ReminderSettings())
    val task = draft ?: return
    val gsd = LocalGsdColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    val dirty by rememberUpdatedState(viewModel.hasUnsavedChanges())
    // A drag or tap that would hide the sheet asks instead while there are edits to lose
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && dirty) {
                askDiscard = true
                false
            } else {
                true
            }
        },
    )
    fun close() {
        viewModel.dismiss()
        onDismiss()
    }
    fun requestClose() {
        if (dirty) askDiscard = true else close()
    }
    ModalBottomSheet(
        // Material can report a dismiss even after the veto above, so the check repeats here
        onDismissRequest = ::requestClose,
        sheetState = sheetState,
        shape = GsdShapes.sheet,
        containerColor = gsd.surface,
    ) {
        val allTasks by viewModel.allTasks.collectAsStateWithLifecycle()
        EditorContent(
            draft = task,
            canSave = task.title.isNotBlank(),
            defaultReminder = reminderDefaults.defaultReminderMinutes,
            onEdit = { transform ->
                val before = viewModel.draft.value
                viewModel.edit(transform)
                val after = viewModel.draft.value
                // The first due date with a reminder is the moment to ask for notifications (§11.2)
                if (before != null && after != null) askForReminders(before, after)
            },
            onSave = {
                viewModel.save(
                    onDone = onDismiss,
                    onError = { error = context.resources.validationMessage(it) },
                    onMissing = {
                        Toast.makeText(context, context.getString(R.string.task_not_found), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                )
            },
            allTasks = allTasks,
            isNew = viewModel.isNew,
            onCancel = ::requestClose,
            error = error,
        )
    }
    if (askDiscard) {
        val keepEditing = {
            askDiscard = false
            // A dismiss Material did not veto has hidden the sheet; bring it back
            if (!sheetState.isVisible) scope.launch { sheetState.show() }
        }
        AlertDialog(
            onDismissRequest = keepEditing,
            title = { Text(stringResource(R.string.editor_discard_title), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.editor_discard_body), style = GsdType.body) },
            confirmButton = {
                TextButton(onClick = {
                    askDiscard = false
                    close()
                }) { Text(stringResource(R.string.editor_discard), color = gsd.alert) }
            },
            dismissButton = {
                TextButton(onClick = keepEditing) { Text(stringResource(R.string.editor_keep_editing)) }
            },
        )
    }
}

/**
 * Stateless editor body: the pinned header (Cancel, title, Save), a save [error] under it, then the
 * sections in spec §8.3 order, scrolling.
 */
@Composable
fun EditorContent(
    draft: Task,
    canSave: Boolean,
    onEdit: ((Task) -> Task) -> Unit,
    onSave: () -> Unit,
    defaultReminder: Int,
    modifier: Modifier = Modifier,
    allTasks: List<Task> = emptyList(),
    isNew: Boolean = true,
    onCancel: (() -> Unit)? = null,
    error: String? = null,
) {
    val gsd = LocalGsdColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        EditorHeader(isNew = isNew, canSave = canSave, onSave = onSave, onCancel = onCancel)
        error?.let {
            Text(
                it,
                style = GsdType.metadata,
                color = gsd.alert,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp).announced(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            EditorSections(draft, onEdit, allTasks, defaultReminder)
        }
    }
}

/** Cancel, the sheet's title, and a filled Save, where the half-open sheet always shows them. */
@Composable
private fun EditorHeader(isNew: Boolean, canSave: Boolean, onSave: () -> Unit, onCancel: (() -> Unit)?) {
    val gsd = LocalGsdColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel), style = GsdType.body, color = gsd.ink2)
            }
        } else {
            Spacer(modifier = Modifier.size(12.dp))
        }
        Text(
            stringResource(if (isNew) R.string.editor_title_new else R.string.editor_title_edit),
            style = GsdType.quadrantTitle,
            color = gsd.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        Button(
            onClick = onSave,
            enabled = canSave,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = gsd.tint,
                contentColor = gsd.inkOnAccent,
                disabledContainerColor = gsd.sunken,
                disabledContentColor = gsd.ink3,
            ),
        ) {
            Text(stringResource(R.string.editor_save), style = GsdType.cardTitle)
        }
    }
}

/** The editable sections, in spec §8.3 order. */
@Composable
private fun EditorSections(draft: Task, onEdit: ((Task) -> Task) -> Unit, allTasks: List<Task>, defaultReminder: Int) {
    val gsd = LocalGsdColors.current
    SectionLabel(R.string.editor_section_title)
    val titleHint = stringResource(R.string.editor_title_hint)
    BasicTextField(
        value = draft.title,
        onValueChange = { new -> onEdit { it.copy(title = new) } },
        textStyle = GsdType.body.copy(color = gsd.ink),
        cursorBrush = gsd.cursor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsdShapes.input)
            .background(gsd.sunken)
            .padding(12.dp)
            .testTag("editor_title"),
        decorationBox = { inner -> HintedField(titleHint, draft.title.isEmpty(), GsdType.body, inner) },
    )

    SectionLabel(R.string.editor_section_quadrant)
    QuadrantPicker(draft, onEdit)
    rememberEscalation(draft)?.let { escalation ->
        // The same "Moves to Do First" the card menu shows, as on web and iOS (spec-escalation.md §5)
        Text(
            escalationMovesTo(escalation),
            style = GsdType.metadata,
            color = gsd.accentInk(escalation.target?.quadrant ?: draft.quadrant),
            modifier = Modifier.testTag("escalation_editor_hint"),
        )
    }

    SectionLabel(R.string.editor_section_tags)
    TagsEditor(draft, onEdit)

    SectionLabel(R.string.editor_section_notes)
    val notesHint = stringResource(R.string.editor_notes_hint)
    BasicTextField(
        value = draft.description,
        onValueChange = { new -> onEdit { it.copy(description = new) } },
        textStyle = GsdType.body.copy(color = gsd.ink),
        cursorBrush = gsd.cursor,
        modifier = Modifier
            .fillMaxWidth()
            .clip(GsdShapes.input)
            .background(gsd.sunken)
            .padding(12.dp)
            .testTag("editor_notes"),
        decorationBox = { inner -> HintedField(notesHint, draft.description.isEmpty(), GsdType.body, inner) },
    )

    val links = detectLinks(draft.description)
    if (links.isNotEmpty()) {
        SectionLabel(R.string.editor_section_links)
        links.forEach { link ->
            Text(link, style = GsdType.subhead, color = gsd.tint)
        }
    }

    SectionLabel(R.string.editor_section_due)
    DueDateSection(draft, onEdit)

    if (draft.dueDate != null) {
        SectionLabel(R.string.editor_section_reminder)
        ReminderSection(draft, defaultReminder, onEdit)
    }

    SectionLabel(R.string.editor_section_repeat)
    RepeatSection(draft, onEdit)

    SectionLabel(R.string.editor_section_subtasks)
    SubtasksSection(draft, onEdit)

    SectionLabel(R.string.editor_section_estimate)
    EstimateSection(draft, onEdit)

    SectionLabel(R.string.editor_section_snooze)
    SnoozeSection(draft, onEdit)

    SectionLabel(R.string.editor_section_dependencies)
    DependenciesSection(draft, allTasks, onEdit)
}

internal fun detectLinks(notes: String): List<String> =
    Regex("""https?://\S+""").findAll(notes).map { it.value }.toList()

@Composable
private fun SectionLabel(labelRes: Int) {
    SectionHeading(stringResource(labelRes))
}

/** 2×2 picker mirroring the matrix (spec §8.3): selected = accent border + wash fill. */
@Composable
private fun QuadrantPicker(draft: Task, onEdit: ((Task) -> Task) -> Unit) {
    val gsd = LocalGsdColors.current
    val rows = listOf(
        listOf(Quadrant.DO_FIRST, Quadrant.SCHEDULE),
        listOf(Quadrant.DELEGATE, Quadrant.ELIMINATE),
    )
    // One radio group: each cell says whether it is the chosen quadrant (review, Accessibility)
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { quadrant ->
                    val selected = draft.quadrant == quadrant
                    val accent = gsd.accent(quadrant)
                    Text(
                        quadrant.title,
                        style = GsdType.subhead,
                        color = if (selected) gsd.accentInk(quadrant) else gsd.ink2,
                        modifier = Modifier
                            .weight(1f)
                            .clip(GsdShapes.small)
                            .background(if (selected) gsd.wash(quadrant) else Color.Transparent)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = accent.copy(alpha = if (selected) 1f else 0.35f),
                                shape = GsdShapes.small,
                            )
                            .selectable(selected = selected, role = Role.RadioButton) {
                                onEdit { it.copy(urgent = quadrant.urgent, important = quadrant.important) }
                            }
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.tags.forEach { tag ->
                    // A 32dp input chip with a 48dp touch area, not a 48dp pill; a tap anywhere removes it (finding 30)
                    InputChip(
                        selected = false,
                        onClick = { onEdit { it.copy(tags = it.tags - tag) } },
                        label = { Text("#$tag", style = GsdType.metadata, color = gsd.accentInk(draft.quadrant)) },
                        trailingIcon = {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.cd_remove_tag, tag),
                                tint = gsd.ink3,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        shape = CircleShape,
                        colors = InputChipDefaults.inputChipColors(containerColor = gsd.wash(draft.quadrant)),
                        border = null,
                    )
                }
            }
        }
        if (draft.tags.size >= MAX_TAGS) {
            // The field goes at the cap, so a 21st tag cannot fail only at Save (finding 30)
            Text(stringResource(R.string.tags_at_limit), style = GsdType.metadata, color = gsd.ink3)
        } else {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.editorField(gsd.sunken),
                contentAlignment = Alignment.CenterStart,
            ) {
                val hint = stringResource(R.string.editor_tag_hint)
                BasicTextField(
                    value = pending,
                    onValueChange = { new ->
                        if (new.endsWith(",")) {
                            pending = new.dropLast(1)
                            commit()
                        } else {
                            pending = new.take(MAX_TAG_LENGTH)
                        }
                    },
                    textStyle = GsdType.subhead.copy(color = gsd.ink),
                    cursorBrush = gsd.cursor,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("editor_tag_input"),
                    decorationBox = { inner -> HintedField(hint, pending.isEmpty(), GsdType.subhead, inner) },
                )
            }
        }
    }
}
