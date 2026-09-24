package dev.vinny.gsd.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdTheme
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.validationMessage
import java.time.Instant
import kotlinx.coroutines.launch

/**
 * The §12.2 share target: a compact "Add to GSD" sheet over the app that shared. The window is
 * see-through and the sheet is a scrolling bottom sheet, so it sits over that app, clear of the
 * status bar and the keyboard (review finding 9).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val sharing = intent?.takeIf { it.action == Intent.ACTION_SEND }
        // As a CharSequence: some apps share styled text, and getStringExtra drops it
        val shared = sharing?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = sharing?.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
        val draft = ShareCapture.buildDraft(shared, subject)
        val container = (application as GsdApp).container

        setContent {
            val themePref by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
            val darkTheme = when (themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            GsdTheme(darkTheme = darkTheme) {
                val gsd = LocalGsdColors.current
                val fallbackTitle = stringResource(R.string.share_fallback_title)
                var title by rememberSaveable { mutableStateOf(draft.title.ifEmpty { fallbackTitle }) }
                var quadrant by rememberSaveable { mutableStateOf(Quadrant.ELIMINATE) } // §12.2 default
                var tagsRaw by rememberSaveable { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                val tags = TaskValidator.normalizeTags(tagsRaw.split(','))

                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = GsdShapes.sheet,
                    containerColor = gsd.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(stringResource(R.string.share_sheet_title), style = GsdType.title, color = gsd.ink)
                        BasicTextField(
                            value = title,
                            onValueChange = { title = ShareCapture.clampTitle(it) },
                            textStyle = GsdType.body.copy(color = gsd.ink),
                            cursorBrush = gsd.cursor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(GsdShapes.input)
                                .background(gsd.sunken)
                                .padding(12.dp)
                                .testTag("share_title"),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Quadrant.entries.forEach { option ->
                                FilterChip(
                                    selected = quadrant == option,
                                    onClick = { quadrant = option },
                                    label = { Text(option.title, style = GsdType.metadata) },
                                )
                            }
                        }
                        Box {
                            if (tagsRaw.isEmpty()) {
                                Text(stringResource(R.string.share_tags_hint), style = GsdType.subhead, color = gsd.ink3)
                            }
                            BasicTextField(
                                value = tagsRaw,
                                onValueChange = { tagsRaw = it },
                                textStyle = GsdType.subhead.copy(color = gsd.ink),
                                cursorBrush = gsd.cursor,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (tags.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                tags.take(6).forEach { tag ->
                                    Text(
                                        "#$tag",
                                        style = GsdType.metadata,
                                        color = gsd.ink2,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(gsd.sunken)
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        if (draft.description.isNotEmpty()) {
                            Text(draft.description, style = GsdType.metadata, color = gsd.tint)
                        }
                        error?.let { Text(it, style = GsdType.metadata, color = gsd.alert) }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.End),
                        ) {
                            TextButton(onClick = { finish() }) {
                                Text(stringResource(R.string.dialog_cancel), color = gsd.ink2)
                            }
                            TextButton(
                                onClick = {
                                    lifecycleScope.launch {
                                        error = add(
                                            Task(
                                                title = title.ifBlank { draft.title },
                                                description = draft.description,
                                                urgent = quadrant.urgent,
                                                important = quadrant.important,
                                                tags = tags,
                                                createdAt = Instant.EPOCH, // store stamps
                                                updatedAt = Instant.EPOCH,
                                            ),
                                        )
                                    }
                                },
                            ) { Text(stringResource(R.string.share_add), color = gsd.tint) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Writes the task and closes, or returns what to tell the user; failures keep the sheet open
     * (§12.2). A validation message is written for people. Anything else is not, so it gets plain copy.
     */
    private suspend fun add(task: Task): String? {
        val container = (application as GsdApp).container
        return try {
            val saved = container.store.add(task)
            enrichSharedTitle(container, saved)
            finish()
            null
        } catch (invalid: TaskValidationError) {
            resources.validationMessage(invalid)
        } catch (failure: Exception) {
            android.util.Log.w("GsdShare", "share failed: ${failure.javaClass.simpleName}")
            getString(R.string.share_failed)
        }
    }
}
