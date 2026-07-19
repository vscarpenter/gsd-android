package dev.vinny.gsd.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TaskValidator
import dev.vinny.gsd.theme.GsdShapes
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.GsdTheme
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.Instant
import kotlinx.coroutines.launch

/** The §12.2 share target: a compact "Add to GSD" sheet over any shared text. */
class ShareActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val draft = ShareCapture.buildDraft(shared)
        val container = (application as GsdApp).container

        setContent {
            GsdTheme {
                val gsd = LocalGsdColors.current
                var title by remember { mutableStateOf(draft.title) }
                var quadrant by remember { mutableStateOf(Quadrant.ELIMINATE) } // §12.2 default
                var tagsRaw by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                val tags = TaskValidator.normalizeTags(tagsRaw.split(','))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GsdShapes.sheet)
                        .background(gsd.surface)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.share_sheet_title), style = GsdType.title, color = gsd.ink)
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it.take(80) },
                        textStyle = GsdType.body.copy(color = gsd.ink),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GsdShapes.input)
                            .background(gsd.sunken)
                            .padding(12.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (tags.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Text(
                            draft.description,
                            style = GsdType.metadata,
                            color = gsd.tint,
                        )
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
                                    try {
                                        container.store.add(
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
                                        finish()
                                    } catch (failure: Exception) {
                                        error = failure.message // failures keep the sheet open (§12.2)
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.share_add), color = gsd.tint) }
                    }
                }
            }
        }
    }
}
