package dev.vinny.gsd.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.editor.reminderLabel
import dev.vinny.gsd.model.ReminderSettings
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import dev.vinny.gsd.ui.formatTimeOfDay
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Settings §8.17.5: reminders, default offset, sound, quiet hours, permission status. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NotificationsGroup(container: AppContainer) {
    val gsd = LocalGsdColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.notificationSettingsStore.observe()
        .collectAsStateWithLifecycle(initialValue = ReminderSettings())

    // Both permissions change in the system's own settings, so re-read them whenever Settings resumes
    var notificationsAllowed by remember { mutableStateOf(notificationsGranted(context)) }
    var exactAllowed by remember { mutableStateOf(container.reminderScheduler.canScheduleExact()) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = notificationsGranted(context)
        exactAllowed = container.reminderScheduler.canScheduleExact()
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { scope.launch { container.notificationSettingsStore.markPermissionAsked() } }

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        scope.launch { container.notificationSettingsStore.update(transform) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel(R.string.settings_notifications)
        SwitchRow(R.string.settings_enable_reminders, settings.enabled) { enabled ->
            update { it.copy(enabled = enabled) }
            // Contextual permission ask on first enablement (spec §11.2)
            if (enabled && !settings.permissionAsked &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.settings_default_reminder), style = GsdType.body, color = gsd.ink)
            // Chips with a selected state and the editor's words, not "1d" and a " ✓" glyph (finding 29)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60, 120, 1440).forEach { minutes ->
                    FilterChip(
                        selected = settings.defaultReminderMinutes == minutes,
                        onClick = { update { it.copy(defaultReminderMinutes = minutes) } },
                        label = { Text(reminderLabel(minutes), style = GsdType.metadata) },
                    )
                }
            }
        }
        SwitchRow(R.string.settings_sound, settings.soundEnabled) { on ->
            update { it.copy(soundEnabled = on) }
        }
        SwitchRow(R.string.settings_quiet_hours, settings.quietHoursStart != null) { on ->
            update {
                if (on) {
                    it.copy(quietHoursStart = LocalTime.of(22, 0), quietHoursEnd = LocalTime.of(7, 0))
                } else {
                    it.copy(quietHoursStart = null, quietHoursEnd = null)
                }
            }
        }
        // Adjustable, in the user's own clock; they were fixed at 22:00 to 07:00 (finding 29)
        val quietStart = settings.quietHoursStart
        val quietEnd = settings.quietHoursEnd
        if (quietStart != null && quietEnd != null) {
            QuietHourRow(R.string.settings_quiet_start, quietStart) { picked -> update { it.copy(quietHoursStart = picked) } }
            QuietHourRow(R.string.settings_quiet_end, quietEnd) { picked -> update { it.copy(quietHoursEnd = picked) } }
        }
        if (!notificationsAllowed) {
            LinkRow(
                stringResource(
                    if (settings.permissionAsked) R.string.settings_notification_denied
                    else R.string.settings_notification_permission,
                ),
            ) {
                if (settings.permissionAsked) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                } else {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (!exactAllowed) {
            // Denied by default on Android 14 and later, and the listing promises on-time reminders (finding 8)
            Text(stringResource(R.string.settings_exact_degraded), style = GsdType.metadata, color = gsd.ochreInk)
            LinkRow(stringResource(R.string.settings_exact_allow)) {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                )
            }
        }
    }
}

/** One end of the quiet hours: its time, which opens a picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuietHourRow(labelRes: Int, time: LocalTime, onPick: (LocalTime) -> Unit) {
    val gsd = LocalGsdColors.current
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button) { picking = true },
    ) {
        Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink, modifier = Modifier.weight(1f))
        Text(formatTimeOfDay(context, time), style = GsdType.body, color = gsd.tint)
    }
    if (picking) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(labelRes)) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    picking = false
                    onPick(LocalTime.of(state.hour, state.minute))
                }) { Text(stringResource(R.string.dialog_done)) }
            },
            dismissButton = {
                TextButton(onClick = { picking = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

@Composable
private fun SwitchRow(labelRes: Int, value: Boolean, onChange: (Boolean) -> Unit) {
    val gsd = LocalGsdColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(labelRes), style = GsdType.body, color = gsd.ink, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun notificationsGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** A tappable line of tinted text, at least 48 dp tall so it is a real touch target. */
@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    val gsd = LocalGsdColors.current
    Text(
        label,
        style = GsdType.body,
        color = gsd.tint,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
