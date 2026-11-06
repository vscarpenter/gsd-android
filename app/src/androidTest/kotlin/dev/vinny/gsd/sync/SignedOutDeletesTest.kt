package dev.vinny.gsd.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.Base64
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.fail

private class QueueTestTokenStore(var stored: String?) : TokenStore {
    override suspend fun token(): String? = stored
    override suspend fun save(token: String) { stored = token }
    override suspend fun clear() { stored = null }
}

/** A server that keeps what it is given and answers a guest with an empty listing, as the list rule does. */
private class RecordingRemote(private val token: () -> String?) : SyncRemote {
    val records = linkedMapOf<String, WireTask>() // taskId -> record
    val deletedTaskIds = mutableListOf<String>()
    private var nextId = 1

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val items = if (token() == null) emptyList() else records.values.toList()
        return TaskListPage(
            page = 1, totalPages = 1, items = items,
            updatedStamps = items.map { PbDates.formatSpace(PbDates.parse(it.clientUpdatedAt) ?: Instant.EPOCH) },
        )
    }
    override suspend fun createTask(wire: WireTask): WireTask {
        val stored = wire.copy(id = "rec${nextId++}")
        records[wire.taskId] = stored
        return stored
    }
    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask {
        records[wire.taskId] = wire.copy(id = recordId)
        return records[wire.taskId]!!
    }
    override suspend fun deleteTask(recordId: String) {
        val taskId = records.entries.first { it.value.id == recordId }.key
        deletedTaskIds += taskId
        records.remove(taskId)
    }
    override suspend fun confirmIdentity(ownerId: String) {
        if (token() == null) throw PocketBaseApi.Unauthorized(401)
    }
}

/**
 * Parity backlog B-56. Signed out, the app still queues every mutation, because the queue is also how a
 * first sign-in uploads local tasks. A delete queued while signed out is a different matter: nothing
 * done signed out should delete on the server. The web never queues while signed out and seeds its
 * tasks on sign-in, so its signed-out Replace import deletes nothing remotely.
 */
@RunWith(AndroidJUnit4::class)
class SignedOutDeletesTest {
    private lateinit var db: GsdDatabase
    private lateinit var prefsFile: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GsdDatabase.buildInMemory(context)
        prefsFile = File.createTempFile("gsd-prefs", ".preferences_pb", context.cacheDir)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        prefsFile.delete()
    }

    private val clock: Clock = Clock.systemUTC()
    private val synced = Task(id = "synced-task", title = "on the server", urgent = true, important = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    private val imported = Task(id = "imported-task", title = "from the backup", urgent = false, important = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    private fun jwt(userId: String, expiresAt: Instant): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val body = encoder.encodeToString("""{"id":"$userId","exp":${expiresAt.epochSecond}}""".toByteArray())
        return "$header.$body.sig"
    }

    private val healthyToken get() = jwt("user-1", clock.instant().plusSeconds(30L * 24 * 3600))

    private suspend fun awaitRunAfter(coordinator: SyncCoordinator, run: Long) {
        withTimeoutOrNull(5_000) { coordinator.finishedRuns.first { it > run } }
            ?: fail("finishedRuns never moved past $run")
    }

    @Test
    fun aReplaceImportMadeSignedOutDeletesNothingOnTheServerAfterTheNextSignIn() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokens = CachedTokenStore(QueueTestTokenStore(healthyToken))
        val auth = AuthManager(PocketBaseApi("http://127.0.0.1:9", { tokens.cachedToken() }), tokens, clock)
        val local = RoomSyncLocalStore(db)
        val remote = RecordingRemote { tokens.cachedToken() }
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { auth.currentUserId() })
        val coordinator = SyncCoordinator(
            context, engine, auth, tokens, db, local,
            SseClient("http://127.0.0.1:9", { tokens.cachedToken() }),
            scope, GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })),
        )
        remote.records[synced.id] = WireMapper.push(synced, "other-device", "user-1").copy(id = "rec0")
        local.upsertFromRemote(synced) // already pulled: no queue row
        coordinator.start()
        awaitRunAfter(coordinator, 0)

        coordinator.signOut()
        TaskStore(db, clock).replaceAll(listOf(imported)) // signed out: queues a DELETE for synced-task

        tokens.save(healthyToken)
        val before = coordinator.finishedRuns.value
        coordinator.confirmOwner(AuthResult(healthyToken, AuthRecord("user-1")), eraseLocalFirst = false)
        awaitRunAfter(coordinator, before)

        assertEquals(emptyList(), remote.deletedTaskIds)
        assertEquals(setOf("synced-task", "imported-task"), remote.records.keys)
        assertEquals(setOf("synced-task", "imported-task"), local.activeTasks().map { it.id }.toSet())
    }
}
