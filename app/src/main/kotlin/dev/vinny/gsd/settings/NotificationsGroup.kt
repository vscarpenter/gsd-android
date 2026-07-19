package dev.vinny.gsd.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.AppContainer
import dev.vinny.gsd.R
import dev.vinny.gsd.model.ReminderSettings
import dev.vinny.gsd.theme.GsdType
import dev.vinny.gsd.theme.LocalGsdColors
import java.time.LocalTime
import kotlinx.coroutines.launch

/** Settings §8.17.5: reminders, default offset, sound, quiet hours, permission status. */
@Composable
internal fun NotificationsGroup(container: AppContainer) {
    val gsd = LocalGsdColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings by container.notificationSettingsStore.observe()
        .collectAsStateWithLifecycle(initialValue = ReminderSettings())

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { scope.launch { container.notificationSettingsStore.markPermissionAsked() } }

    fun update(transform: (ReminderSettings) -> ReminderSettings) {
        scope.launch { container.notificationSettingsStore.update(transform) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_notifications).uppercase(),
            style = GsdType.caption,
            color = gsd.ink3,
            modifier = Modifier.padding(top = 12.dp),
        )
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_default_reminder), style = GsdType.body, color = gsd.ink,
                modifier = Modifier.weight(1f))
            listOf(15, 30, 60, 120, 1440).forEach { minutes ->
                TextButton(onClick = { update { it.copy(defaultReminderMinutes = minutes) } }) {
                    val label = when (minutes) {
                        1440 -> "1d"
                        in 60..120 -> "${minutes / 60}h"
                        else -> "${minutes}m"
                    }
                    Text(
                        label + if (settings.defaultReminderMinutes == minutes) " ✓" else "",
                        style = GsdType.metadata,
                        color = gsd.tint,
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
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            Text(
                stringResource(
                    if (settings.permissionAsked) R.string.settings_notification_denied
                    else R.string.settings_notification_permission,
                ),
                style = GsdType.body,
                color = gsd.tint,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (settings.permissionAsked) {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    .padding(vertical = 4.dp),
            )
        }
        if (!container.reminderScheduler.canScheduleExact()) {
            Text(stringResource(R.string.settings_exact_degraded), style = GsdType.metadata, color = gsd.ochre)
        }
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
