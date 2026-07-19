package dev.vinny.gsd.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.vinny.gsd.GsdApp
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Alarms die on reboot (spec §11.2) — reschedule everything from the store. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val container = (context.applicationContext as GsdApp).container
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderScheduler.resyncAll(
                    container.store.observeTasks().first(),
                    container.notificationSettingsStore.get(),
                    Instant.now(),
                )
            } finally {
                result.finish()
            }
        }
    }
}
