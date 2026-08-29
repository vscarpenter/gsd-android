package dev.vinny.gsd

import android.app.Application
import dev.vinny.gsd.notifications.Channels
import dev.vinny.gsd.notifications.ReminderResync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GsdApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Channels.ensure(this)
        ReminderResync(container.store, container.notificationSettingsStore, container.reminderScheduler)
            .start(appScope)
        container.startSync(appScope, this)
        // Retention purge at launch (parity spec P§3) — don't wait on the periodic worker.
        appScope.launch { container.trashStore.purgeExpired() }
        dev.vinny.gsd.notifications.MaintenanceWorker.register(this)
        // Glance is push-updated: snapshot recomputed on every relevant data change (spec §12.1)
        appScope.launch {
            container.store.observeTasks().collect { tasks ->
                dev.vinny.gsd.widget.writeWidgetSnapshot(
                    this@GsdApp,
                    dev.vinny.gsd.widget.WidgetSnapshot.build(
                        tasks,
                        java.time.Instant.now(),
                        java.time.ZoneId.systemDefault(),
                    ),
                )
            }
        }
    }
}
