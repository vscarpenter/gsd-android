package dev.vinny.gsd.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.FilterCriteria
import dev.vinny.gsd.model.FilterStatus
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import kotlinx.coroutines.launch

/** Create/edit a custom smart view (spec §8.13): name, curated icon, criteria controls. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartViewEditorScreen(
    container: AppContainer,
    viewId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val browseViewModel: BrowseViewModel =
        viewModel { BrowseViewModel(container.store, container.smartViewStore) }
    val state by browseViewModel.state.collectAsStateWithLifecycle()
    val existing = viewId?.let { id -> state.custom.map { it.view }.firstOrNull { it.id == id } }

    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(curatedCustomIcons.first()) }
    var criteria by remember { mutableStateOf(FilterCriteria()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(existing) {
        if (existing != null && !loaded) {
            name = existing.name; icon = existing.icon; criteria = existing.criteria; loaded = true
        }
    }

    val gsd = LocalGsdColors.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.view_editor_title), style = GsdType.title, color = gsd.ink)

        Box {
            if (name.isEmpty()) {
                Text(stringResource(R.string.view_editor_name_hint), style = GsdType.body, color = gsd.ink3)
            }
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                textStyle = GsdType.body.copy(color = gsd.ink),
                cursorBrush = gsd.cursor,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("view_name"),
            )
        }

        Text(stringResource(R.string.view_editor_icon).uppercase(), style = GsdType.caption, color = gsd.ink3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            curatedCustomIcons.forEach { token -> IconChoice(token, icon == token) { icon = token } }
        }

        Text(stringResource(R.string.view_editor_status).uppercase(), style = GsdType.caption, color = gsd.ink3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                FilterStatus.ALL to R.string.status_all,
                FilterStatus.ACTIVE to R.string.status_active,
                FilterStatus.COMPLETED to R.string.status_completed,
            ).forEach { (status, labelRes) ->
                FilterChip(
                    selected = criteria.status == status,
                    onClick = { criteria = criteria.copy(status = status) },
                    label = { Text(stringResource(labelRes), style = GsdType.metadata) },
                )
            }
        }

        Text(stringResource(R.string.view_editor_quadrants).uppercase(), style = GsdType.caption, color = gsd.ink3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Quadrant.entries.forEach { quadrant ->
                FilterChip(
                    selected = quadrant.id in criteria.quadrants,
                    onClick = {
                        criteria = criteria.copy(
                            quadrants = if (quadrant.id in criteria.quadrants) {
                                criteria.quadrants - quadrant.id
                            } else {
                                criteria.quadrants + quadrant.id
                            },
                        )
                    },
                    label = { Text(quadrant.title, style = GsdType.metadata) },
                )
            }
        }

        Text(stringResource(R.string.view_editor_toggles).uppercase(), style = GsdType.caption, color = gsd.ink3)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ConditionChip(R.string.cond_overdue, criteria.overdue) { criteria = criteria.copy(overdue = it) }
            ConditionChip(R.string.cond_due_today, criteria.dueToday) { criteria = criteria.copy(dueToday = it) }
            ConditionChip(R.string.cond_due_this_week, criteria.dueThisWeek) { criteria = criteria.copy(dueThisWeek = it) }
            ConditionChip(R.string.cond_no_due_date, criteria.noDueDate) { criteria = criteria.copy(noDueDate = it) }
            ConditionChip(R.string.cond_recently_added, criteria.recentlyAdded) { criteria = criteria.copy(recentlyAdded = it) }
            ConditionChip(R.string.cond_recently_completed, criteria.recentlyCompleted) { criteria = criteria.copy(recentlyCompleted = it) }
            ConditionChip(R.string.cond_ready_to_work, criteria.readyToWork) { criteria = criteria.copy(readyToWork = it) }
        }

        TextButton(
            onClick = {
                scope.launch {
                    if (existing != null) {
                        container.smartViewStore.update(existing.copy(name = name, icon = icon, criteria = criteria))
                    } else {
                        container.smartViewStore.create(name, icon, criteria)
                    }
                    onDone()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.view_editor_save), style = GsdType.cardTitle,
                color = if (name.isNotBlank()) gsd.tint else gsd.ink3)
        }
    }
}

@Composable
private fun IconChoice(token: String, selected: Boolean, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    IconButton(onClick = onClick) {
        Icon(
            smartViewIcons[token] ?: Icons.Outlined.Bolt,
            contentDescription = token,
            tint = if (selected) gsd.tint else gsd.ink3,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ConditionChip(labelRes: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = value,
        onClick = { onChange(!value) },
        label = { Text(stringResource(labelRes), style = GsdType.metadata) },
    )
}
