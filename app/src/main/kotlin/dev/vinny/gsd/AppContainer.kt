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
}
