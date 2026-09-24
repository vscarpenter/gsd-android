package dev.vinny.gsd.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.vinny.gsd.R
import dev.vinny.gsd.model.CaptureParser
import dev.vinny.gsd.model.ParsedCapture
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor

data class CaptureState(val text: String = "", val override: Quadrant? = null) {
    val parsed: ParsedCapture get() = CaptureParser.parse(text)
    val effectiveQuadrant: Quadrant get() = override ?: Quadrant.from(parsed.urgent, parsed.important)
    val isAuto: Boolean get() = override == null

    /** Auto → Q1 → Q2 → Q3 → Q4 → Auto (spec §8.2). */
    fun cycled(): CaptureState = copy(
        override = when (override) {
            null -> Quadrant.DO_FIRST
            Quadrant.DO_FIRST -> Quadrant.SCHEDULE
            Quadrant.SCHEDULE -> Quadrant.DELEGATE
            Quadrant.DELEGATE -> Quadrant.ELIMINATE
            Quadrant.ELIMINATE -> null
        },
    )
}

private val quadrantIcons = mapOf(
    Quadrant.DO_FIRST to Icons.Outlined.LocalFireDepartment,
    Quadrant.SCHEDULE to Icons.Outlined.CalendarMonth,
    Quadrant.DELEGATE to Icons.Outlined.Group,
    Quadrant.ELIMINATE to Icons.Outlined.Delete,
)

/** The pinned capture pill (spec §5.10) with live parse preview. Stateless. */
@Composable
fun CaptureBarContent(
    state: CaptureState,
    onTextChange: (String) -> Unit,
    onCycleOverride: () -> Unit,
    onSubmit: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gsd = LocalGsdColors.current
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(gsd.surface)
                .border(1.dp, gsd.hairline, CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (state.text.isEmpty()) {
                    Text(stringResource(R.string.capture_placeholder), style = GsdType.body, color = gsd.ink3)
                }
                BasicTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    textStyle = GsdType.body.copy(color = gsd.ink),
                    cursorBrush = gsd.cursor,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("capture_field"),
                )
            }
            QuadrantChip(state, onCycleOverride)
        }
        val tags = state.parsed.tags
        if (tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
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
        if (state.text.isNotEmpty()) {
            TextButton(onClick = onDetails) {
                Text(stringResource(R.string.capture_details), style = GsdType.metadata, color = gsd.tint)
            }
        }
    }
}

@Composable
private fun QuadrantChip(state: CaptureState, onCycleOverride: () -> Unit) {
    val gsd = LocalGsdColors.current
    val showAuto = state.isAuto && state.text.isEmpty()
    val quadrant = state.effectiveQuadrant
    val accent = if (showAuto) gsd.ink3 else gsd.accent(quadrant)
    val description = stringResource(R.string.cd_capture_quadrant_chip)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(gsd.surface2)
            .border(1.dp, gsd.hairlineStrong, CircleShape)
            .clickable(onClick = onCycleOverride)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (showAuto) Icons.Outlined.GridView else quadrantIcons.getValue(quadrant),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Text(
            if (showAuto) stringResource(R.string.capture_auto) else quadrant.title,
            style = GsdType.metadata,
            color = accent,
        )
    }
}
