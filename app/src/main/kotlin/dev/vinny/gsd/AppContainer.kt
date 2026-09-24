package dev.vinny.gsd

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.preferencesDataStore
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.ArchiveStore
import dev.vinny.gsd.store.BackupAssembler
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.ImportApplier
import dev.vinny.gsd.store.SmartViewStore
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
import java.time.Clock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore(name = "gsd_prefs")

/**
 * Manual composition root (spec §3 — no DI framework). One database, one
 * store, one prefs instance, shared everywhere.
 */
class AppContainer(context: Context) {
    val clock: Clock = Clock.systemUTC()

    /**
     * Work for the life of the process: sync, reminders, and a user's import, export, erase, or
     * account change, which a rotation must not cancel midway (review finding 40). A failure there is
     * logged, never fatal: a widget write or a purge that throws must not take the app down. The log
     * names only the exception's class, because a message can carry task text (spec §13.4).
     */
    val appScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, error ->
            Log.e("GsdApp", "background work failed: ${error.javaClass.simpleName}")
        },
    )

    /** What that work has to tell the user, shown once by Settings, even after a trip elsewhere. */
    val settingsMessages = dev.vinny.gsd.palette.OneShotRequest<String>()

    /** The last account deletion's outcome. It stays until the next sign-in attempt (spec §8.21). */
    val accountDeletionMessage = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)

    val db: GsdDatabase = GsdDatabase.build(context)

    val store: TaskStore = TaskStore(db, clock)

    val archiveStore: ArchiveStore = ArchiveStore(db, clock)

    val trashStore: TrashStore = TrashStore(db, clock)

    val smartViewStore: SmartViewStore = SmartViewStore(db, clock)

    val backupAssembler: BackupAssembler = BackupAssembler(db)

    val importApplier: ImportApplier = ImportApplier(db, store, clock)

    val prefs: GsdPrefs = GsdPrefs(context.dataStore)

    val notificationSettingsStore: dev.vinny.gsd.store.NotificationSettingsStore =
        dev.vinny.gsd.store.NotificationSettingsStore(db, clock)

    val reminderScheduler: dev.vinny.gsd.notifications.ReminderScheduler =
        dev.vinny.gsd.notifications.ReminderScheduler(context.applicationContext)

    val palette: PaletteController = PaletteController()

    val tokenStore: dev.vinny.gsd.auth.CachedTokenStore =
        dev.vinny.gsd.auth.CachedTokenStore(dev.vinny.gsd.auth.KeystoreTokenStore(context.applicationContext))

    val api: dev.vinny.gsd.sync.PocketBaseApi =
        dev.vinny.gsd.sync.PocketBaseApi(BACKEND_URL, { tokenStore.cachedToken() })

    val feedbackApi: dev.vinny.gsd.sync.FeedbackApi = dev.vinny.gsd.sync.FeedbackApi(baseUrl = BACKEND_URL)

    val authManager: dev.vinny.gsd.sync.AuthManager =
        dev.vinny.gsd.sync.AuthManager(api, tokenStore, clock)

    val syncLocal: dev.vinny.gsd.sync.RoomSyncLocalStore = dev.vinny.gsd.sync.RoomSyncLocalStore(db)

    val syncEngine: dev.vinny.gsd.sync.SyncEngine =
        dev.vinny.gsd.sync.SyncEngine(api, syncLocal, clock, ownerId = { authManager.currentUserId() })

    val sseClient: dev.vinny.gsd.sync.SseClient =
        dev.vinny.gsd.sync.SseClient(BACKEND_URL, { tokenStore.cachedToken() })

    lateinit var syncCoordinator: dev.vinny.gsd.sync.SyncCoordinator
        private set

    fun startSync(scope: kotlinx.coroutines.CoroutineScope, context: Context) {
        syncCoordinator = dev.vinny.gsd.sync.SyncCoordinator(
            context, syncEngine, authManager, tokenStore, db, syncLocal, sseClient, scope, prefs,
        )
        syncCoordinator.lastOwnerId = { prefs.lastOwnerId.first() }
        syncCoordinator.onOwnerConfirmed = { result ->
            prefs.setLastOwner(result.record.id, result.record.email.ifBlank { null })
            prefs.setLastAuthProvider(syncCoordinator.providerFor(result.record.id))
        }
        syncCoordinator.start()
    }

    companion object {
        const val BACKEND_URL = "https://api.vinny.io"
        const val SITE_URL = "https://gsdtaskmanager.com"
        const val PRIVACY_POLICY_URL = "$SITE_URL/privacy/"
    }
}

/** Bridges hardware shortcuts (§6.6) and notification taps to whichever screen hosts them. */
class PaletteController {
    /** Delivered once. A StateFlow would replay the request to every new Matrix composition (§6.4). */
    val openRequests = dev.vinny.gsd.palette.OneShotRequest<Unit>()
    val newTaskRequests = dev.vinny.gsd.palette.OneShotRequest<Unit>()
    val openTaskRequests = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** `gsd://quadrant/<id>` and Ctrl+1 to Ctrl+4: the Matrix scrolls to that quadrant (§6.4, §6.6). */
    val quadrantRequests = dev.vinny.gsd.palette.OneShotRequest<dev.vinny.gsd.model.Quadrant>()
    /** §6.4 deep link held until the nav host consumes it — cold-start safe. */
    val pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<dev.vinny.gsd.nav.GsdRoute?>(null)

    fun requestOpen() {
        openRequests.send(Unit)
    }

    fun requestNewTask() {
        newTaskRequests.send(Unit)
    }

    fun requestOpenTask(taskId: String) {
        openTaskRequests.value = taskId
    }

    fun requestQuadrant(quadrant: dev.vinny.gsd.model.Quadrant) {
        quadrantRequests.send(quadrant)
    }
}
