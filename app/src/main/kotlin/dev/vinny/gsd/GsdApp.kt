package dev.vinny.gsd

import android.app.Application
import android.util.Log
import dev.vinny.gsd.notifications.Channels
import dev.vinny.gsd.notifications.ReminderResync
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GsdApp : Application() {
    lateinit var container: AppContainer
        private set

    /**
     * Background work for the life of the process. A failure there is logged, never fatal: a widget
     * write or a purge that throws must not take the app down. The log names only the exception's
     * class, because a message can carry task text (spec §13.4).
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e("GsdApp", "background work failed: ${error.javaClass.simpleName}")
        },
    )

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
