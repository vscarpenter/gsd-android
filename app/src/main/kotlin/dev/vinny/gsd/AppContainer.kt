package dev.vinny.gsd

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.ArchiveStore
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SmartViewStore
import dev.vinny.gsd.store.TaskStore
import java.time.Clock

private val Context.dataStore by preferencesDataStore(name = "gsd_prefs")

/**
 * Manual composition root (spec §3 — no DI framework). One database, one
 * store, one prefs instance, shared everywhere.
 */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()

    val db: GsdDatabase = GsdDatabase.build(context)

    val store: TaskStore = TaskStore(db, clock)

    val archiveStore: ArchiveStore = ArchiveStore(db, clock)

    val smartViewStore: SmartViewStore = SmartViewStore(db, clock)

    val prefs: GsdPrefs = GsdPrefs(context.dataStore)

    val notificationSettingsStore: dev.vinny.gsd.store.NotificationSettingsStore =
        dev.vinny.gsd.store.NotificationSettingsStore(db, clock)

    val reminderScheduler: dev.vinny.gsd.notifications.ReminderScheduler =
        dev.vinny.gsd.notifications.ReminderScheduler(context.applicationContext)

    val palette: PaletteController = PaletteController()
}

/** Bridges hardware shortcuts (§6.6) and notification taps to whichever screen hosts them. */
class PaletteController {
    val openRequests = kotlinx.coroutines.flow.MutableStateFlow(0)
    val newTaskRequests = kotlinx.coroutines.flow.MutableStateFlow(0)
    val openTaskRequests = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    fun requestOpen() {
        openRequests.value++
    }

    fun requestNewTask() {
        newTaskRequests.value++
    }

    fun requestOpenTask(taskId: String) {
        openTaskRequests.value = taskId
    }
}
