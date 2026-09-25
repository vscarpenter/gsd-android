package dev.vinny.gsd.notifications

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vinny.gsd.model.ReminderPermission
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.NotificationSettingsStore
import kotlinx.coroutines.launch

/**
 * The contextual notification-permission ask (spec §11.2, review finding 7). The returned function
 * takes an edit's before and after, and asks once, the first time an edit gives a task a due date
 * with a reminder. Reminders default to on, so waiting for the Settings switch meant it never asked.
 */
@Composable
fun rememberReminderPermissionAsk(settingsStore: NotificationSettingsStore): (before: Task, after: Task) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by settingsStore.observe().collectAsStateWithLifecycle(initialValue = null)
    // permissionAsked lands only when the dialog answers, so a second quick edit must not ask again first
    var launched by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        scope.launch { settingsStore.markPermissionAsked() }
    }
    return { before, after ->
        val current = settings
        // Below Android 13 there is nothing to ask for, and the launcher's answer would read as a denial
        val granted = notificationPermissionGranted(context)
        if (!launched && current != null && ReminderPermission.shouldAsk(current, granted, before, after)) {
            launched = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
