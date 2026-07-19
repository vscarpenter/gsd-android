package dev.vinny.gsd

import android.app.Application
import dev.vinny.gsd.notifications.Channels
import dev.vinny.gsd.notifications.ReminderResync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
        dev.vinny.gsd.notifications.MaintenanceWorker.register(this)
    }
}
