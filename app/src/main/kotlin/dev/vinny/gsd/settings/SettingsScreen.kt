package dev.vinny.gsd.settings

import dev.vinny.gsd.model.EscalationWindow
import androidx.compose.ui.res.pluralStringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.palette.ConsumeWhileResumed
import dev.vinny.gsd.prefs.ThemePref
import dev.vinny.gsd.store.ArchiveSettingsEntity
import dev.vinny.gsd.store.ImportMode
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.theme.cursor
import dev.vinny.gsd.ui.HintedField
import dev.vinny.gsd.ui.SectionHeading
import kotlinx.coroutines.launch

/** The settings surface (spec §8.17). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSyncHistory: () -> Unit = {},
    onOpenFeedback: () -> Unit = {},
) {
    val gsd = LocalGsdColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val theme by container.prefs.theme.collectAsStateWithLifecycle(initialValue = ThemePref.SYSTEM)
    val showCompleted by container.prefs.showCompleted.collectAsStateWithLifecycle(initialValue = true)
    val escalationWindow by container.prefs.escalationWindow.collectAsStateWithLifecycle(initialValue = EscalationWindow.DEFAULT)
    val fetchTitles by container.prefs.fetchShareTitles.collectAsStateWithLifecycle(initialValue = false)
    val archiveSettings by container.db.archiveSettingsDao().observe()
        .collectAsStateWithLifecycle(initialValue = null)

    var pendingImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var eraseOpen by remember { mutableStateOf(false) }

    // Export, import, and erase run in the app's scope, so a rotation cannot cancel them (finding 40)
    val appContext = context.applicationContext
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) container.appScope.launch {
            exportBackup(container.backupAssembler, container.settingsMessages, appContext, uri)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImport = uri }

    ConsumeWhileResumed(container.settingsMessages) { snackbar.showSnackbar(it) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupLabel(R.string.settings_appearance)
            Text(stringResource(R.string.settings_theme), style = GsdType.cardTitle, color = gsd.ink)
            Column(modifier = Modifier.selectableGroup()) {
                listOf(
                    ThemePref.SYSTEM to R.string.theme_system,
                    ThemePref.LIGHT to R.string.theme_light,
                    ThemePref.DARK to R.string.theme_dark,
                ).forEach { (pref, labelRes) ->
                    // A whole 48dp row picks, with the radio set apart from its label (finding 29)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(
                                selected = theme == pref,
                                role = Role.RadioButton,
                                onClick = { scope.launch { container.prefs.setTheme(pref) } },
                            ),
                    ) {
                        RadioButton(selected = theme == pref, onClick = null)
                        Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink)
                    }
                }
            }
            ToggleRow(R.string.settings_show_completed, showCompleted) {
                scope.launch { container.prefs.setShowCompleted(it) }
            }
            Text(stringResource(R.string.settings_escalation), style = GsdType.cardTitle, color = gsd.ink)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EscalationWindow.OPTIONS.forEach { days ->
                    FilterChip(
                        selected = escalationWindow == days,
                        onClick = { scope.launch { container.prefs.setEscalationWindow(days) } },
                        label = { Text(escalationOptionLabel(days)) },
                    )
                }
            }
            Text(stringResource(R.string.settings_escalation_footer), style = GsdType.metadata, color = gsd.ink3)

            GroupLabel(R.string.settings_sharing)
            ToggleRow(R.string.settings_fetch_titles, fetchTitles) {
                scope.launch { container.prefs.setFetchShareTitles(it) }
            }
            Text(stringResource(R.string.settings_fetch_titles_footer), style = GsdType.metadata, color = gsd.ink3)

            AccountGroup(container, onOpenHistory = onOpenSyncHistory)

            GroupLabel(R.string.settings_archive)
            ToggleRow(R.string.settings_auto_archive, archiveSettings?.enabled ?: false) { enabled ->
                scope.launch {
                    container.db.archiveSettingsDao().upsert(
                        ArchiveSettingsEntity(enabled = enabled, archiveAfterDays = archiveSettings?.archiveAfterDays ?: 30),
                    )
                }
            }
            // Chips with a selected state, not a " ✓" glyph; Archive now is its own row (finding 29)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { days ->
                    FilterChip(
                        selected = (archiveSettings?.archiveAfterDays ?: 30) == days,
                        onClick = {
                            scope.launch {
                                container.db.archiveSettingsDao().upsert(
                                    ArchiveSettingsEntity(
                                        enabled = archiveSettings?.enabled ?: false,
                                        archiveAfterDays = days,
                                    ),
                                )
                            }
                        },
                        label = { Text(pluralStringResource(R.plurals.day_count, days, days), style = GsdType.metadata) },
                    )
                }
            }
            ActionRow(R.string.archive_now) {
                scope.launch { container.archiveStore.sweep(archiveSettings?.archiveAfterDays ?: 30) }
            }

            NotificationsGroup(container)

            GroupLabel(R.string.settings_data)
            ActionRow(R.string.settings_export) { exportLauncher.launch("gsd-export.json") }
            ActionRow(R.string.settings_import) { importLauncher.launch(arrayOf("application/json", "text/*")) }
            ActionRow(R.string.settings_erase, tint = gsd.alert) { eraseOpen = true }

            // Its own group, as spec §8.17 lists it; it sat at the end of Data & storage
            GroupLabel(R.string.settings_feedback)
            ActionRow(R.string.settings_send_feedback, onClick = onOpenFeedback)

            AboutGroup(container, onOpenHelp)
        }
        // Over the scroll, not at its end, so a message shows wherever the user has scrolled to
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_mode_title), style = GsdType.cardTitle) },
            text = { Text(stringResource(R.string.import_mode_body), style = GsdType.body, color = gsd.ink2) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    container.appScope.launch {
                        importBackup(container.importApplier, container.settingsMessages, appContext, uri, ImportMode.MERGE)
                    }
                }) { Text(stringResource(R.string.import_merge)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingImport = null
                    container.appScope.launch {
                        importBackup(container.importApplier, container.settingsMessages, appContext, uri, ImportMode.REPLACE)
                    }
                }) { Text(stringResource(R.string.import_replace)) }
            },
        )
    }

    if (eraseOpen) {
        var confirmText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { eraseOpen = false },
            title = { Text(stringResource(R.string.erase_title), style = GsdType.cardTitle) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.erase_body), style = GsdType.body, color = gsd.ink2)
                    val hint = stringResource(R.string.erase_confirm_hint)
                    BasicTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        textStyle = GsdType.body.copy(color = gsd.ink),
                        cursorBrush = gsd.cursor,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("erase_confirm_field"),
                        decorationBox = { inner -> HintedField(hint, confirmText.isEmpty(), GsdType.body, inner) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        eraseOpen = false
                        container.appScope.launch { eraseEverything(container, appContext) }
                    },
                    enabled = confirmText == "RESET",
                ) { Text(stringResource(R.string.erase_confirm), color = gsd.alert) }
            },
            dismissButton = {
                TextButton(onClick = { eraseOpen = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
internal fun GroupLabel(labelRes: Int) {
    SectionHeading(stringResource(labelRes), Modifier.padding(top = 12.dp))
}

@Composable
private fun ToggleRow(labelRes: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    val gsd = LocalGsdColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

/** A tappable row of tinted text, at least 48dp tall; rows had measured under 48dp (finding 29). */
@Composable
internal fun ActionRow(labelRes: Int, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    Text(
        stringResource(labelRes),
        style = GsdType.body,
        color = tint ?: gsd.tint,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}

@Composable
private fun escalationOptionLabel(days: Int?): String = when (days) {
    null -> stringResource(R.string.escalation_off)
    7 -> stringResource(R.string.escalation_week)
    else -> pluralStringResource(R.plurals.escalation_days, days, days)
}
