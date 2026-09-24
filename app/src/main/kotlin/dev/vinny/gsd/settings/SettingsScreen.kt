package dev.vinny.gsd.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
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
import kotlinx.coroutines.launch

/** The settings surface (spec §8.17). */
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
            listOf(
                ThemePref.SYSTEM to R.string.theme_system,
                ThemePref.LIGHT to R.string.theme_light,
                ThemePref.DARK to R.string.theme_dark,
            ).forEach { (pref, labelRes) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.selectable(
                        selected = theme == pref,
                        onClick = { scope.launch { container.prefs.setTheme(pref) } },
                    ),
                ) {
                    RadioButton(selected = theme == pref, onClick = null)
                    Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink)
                }
            }
            ToggleRow(R.string.settings_show_completed, showCompleted) {
                scope.launch { container.prefs.setShowCompleted(it) }
            }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 90).forEach { days ->
                    TextButton(onClick = {
                        scope.launch {
                            container.db.archiveSettingsDao().upsert(
                                ArchiveSettingsEntity(
                                    enabled = archiveSettings?.enabled ?: false,
                                    archiveAfterDays = days,
                                ),
                            )
                        }
                    }) {
                        Text(
                            "${days}d" + if ((archiveSettings?.archiveAfterDays ?: 30) == days) " ✓" else "",
                            style = GsdType.metadata,
                            color = gsd.tint,
                        )
                    }
                }
                TextButton(onClick = {
                    scope.launch { container.archiveStore.sweep(archiveSettings?.archiveAfterDays ?: 30) }
                }) { Text(stringResource(R.string.archive_now), style = GsdType.metadata, color = gsd.tint) }
            }

            NotificationsGroup(container)

            GroupLabel(R.string.settings_data)
            ActionRow(R.string.settings_export) { exportLauncher.launch("gsd-export.json") }
            ActionRow(R.string.settings_import) { importLauncher.launch(arrayOf("application/json", "text/*")) }
            ActionRow(R.string.settings_erase, tint = gsd.alert) { eraseOpen = true }
            ActionRow(R.string.settings_feedback, onClick = onOpenFeedback)

            GroupLabel(R.string.settings_about)
            Text(
                stringResource(R.string.settings_version, dev.vinny.gsd.BuildConfig.VERSION_NAME),
                style = GsdType.metadata,
                color = gsd.ink2,
            )
            Text(stringResource(R.string.settings_privacy_blurb), style = GsdType.metadata, color = gsd.ink3)
            ActionRow(R.string.settings_field_guide, onClick = onOpenHelp)
            ActionRow(R.string.settings_show_onboarding) {
                scope.launch { container.prefs.setHasOnboarded(false) }
            }
        }
        // Over the scroll, not at its end, so a message shows wherever the user has scrolled to
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_mode_title), style = GsdType.cardTitle) },
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
                    BasicTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        textStyle = GsdType.body.copy(color = gsd.ink),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("erase_confirm_field"),
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
private fun GroupLabel(labelRes: Int) {
    Text(
        stringResource(labelRes).uppercase(),
        style = GsdType.caption,
        color = LocalGsdColors.current.ink3,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun ToggleRow(labelRes: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    val gsd = LocalGsdColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(labelRes: Int, tint: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    Text(
        stringResource(labelRes),
        style = GsdType.body,
        color = tint ?: gsd.tint,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    )
}
