package dev.vinny.gsd.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.ArchiveStore
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SyncOp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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

    /** Lists only records updated after the cursor, as PocketBase's `updated > cursor` filter does. */
    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val since = PbDates.parse(updatedCursor) ?: Instant.EPOCH
        val items = if (token() == null) emptyList() else records.values.filter { (PbDates.parse(it.clientUpdatedAt) ?: Instant.EPOCH) > since }
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
    private val syncedAt = Instant.parse("2026-06-01T12:00:00.000Z")
    private val synced = Task(id = "synced-task", title = "on the server", urgent = true, important = true,
        createdAt = syncedAt, updatedAt = syncedAt)
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

    /**
     * The run this test is about is the one that uploads the import. Waiting for "a run finished" is not
     * enough: the network callback or the pending-count debounce can request a run of their own, and one
     * of those finishing first let the assertions run before the sign-in's run had pushed anything.
     */
    private suspend fun awaitRunThatPushed(coordinator: SyncCoordinator, count: Int) {
        withTimeoutOrNull(5_000) { coordinator.lastResult.first { it != null && it.pushed == count } }
            ?: fail("no run pushed $count items; last result ${coordinator.lastResult.value}")
    }

    private class Fixture(val coordinator: SyncCoordinator, val tokens: CachedTokenStore, val local: RoomSyncLocalStore, val remote: RecordingRemote)

    /** Signed in, with one task already on the server and on the device. */
    private suspend fun signedInFixture(): Fixture {
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
        remote.records[synced.id] = WireMapper.push(synced, "other-device", "user-1", defaultReminder = 15).copy(id = "rec0")
        local.upsertFromRemote(synced) // already pulled: no queue row
        // The cursor sits past the synced task and the contract is current, so no later pull lists it again
        local.saveCursor(PbDates.formatSpace(clock.instant()))
        local.savePullContractVersion(SyncEngine.PULL_CONTRACT_VERSION)
        coordinator.start()
        awaitRunAfter(coordinator, 0)
        return Fixture(coordinator, tokens, local, remote)
    }

    private suspend fun signOutAndDrain(f: Fixture) {
        f.coordinator.signOut()
        withTimeoutOrNull(5_000) { f.coordinator.syncing.first { !it } } ?: fail("a run outlived the sign-out")
    }

    private suspend fun signInAgain(f: Fixture) {
        f.tokens.save(healthyToken)
        f.coordinator.confirmOwner(AuthResult(healthyToken, AuthRecord("user-1")), eraseLocalFirst = false)
    }

    private suspend fun queuedDeleteFor(taskId: String): Boolean =
        db.syncQueueDao().pending().any { it.taskId == taskId && it.operation == SyncOp.DELETE }

    /** Polls for an end state the test is about, since several triggers can finish runs of their own. */
    private suspend fun awaitState(what: String, condition: suspend () -> Boolean) {
        withTimeoutOrNull(5_000) { while (!condition()) delay(50) } ?: fail("never reached: $what")
    }

    @Test
    fun aReplaceImportMadeSignedOutDeletesNothingOnTheServerAfterTheNextSignIn() = runBlocking {
        val f = signedInFixture()
        signOutAndDrain(f)
        TaskStore(db, clock).replaceAll(listOf(imported)) // signed out: queues a DELETE for synced-task
        assertTrue(queuedDeleteFor(synced.id), "the replace queued no delete, so the test proves nothing")

        signInAgain(f)
        awaitRunThatPushed(f.coordinator, count = 1) // the import's UPDATE; the dropped DELETE would be a second push

        assertEquals(emptyList(), f.remote.deletedTaskIds)
        assertEquals(setOf("synced-task", "imported-task"), f.remote.records.keys)
    }

    /**
     * Resurrect on sight (spec §8.21, §9.4): the replaced-away task's server copy is older than the kept
     * cursor, so no pull lists it. The sign-in remembers the dropped delete and the run brings it back.
     */
    @Test
    fun aTaskReplacedAwaySignedOutComesBackAfterTheNextSignIn() = runBlocking {
        val f = signedInFixture()
        signOutAndDrain(f)
        TaskStore(db, clock).replaceAll(listOf(imported))
        assertTrue(queuedDeleteFor(synced.id), "the replace queued no delete, so the test proves nothing")

        signInAgain(f)
        awaitRunThatPushed(f.coordinator, count = 1)

        assertEquals(setOf("synced-task", "imported-task"), f.local.activeTasks().map { it.id }.toSet())
    }

    /** The owner's rule: a task deleted while signed out reappears when the server still has it. */
    @Test
    fun aTaskTrashedSignedOutComesBackOutOfTrashAfterTheNextSignIn() = runBlocking {
        val f = signedInFixture()
        signOutAndDrain(f)
        TaskStore(db, clock).delete(synced.id) // to Trash, with a DELETE row
        assertTrue(queuedDeleteFor(synced.id) && db.deletedTaskDao().byId(synced.id) != null, "the delete did not land")

        signInAgain(f)
        awaitState("synced-task active again") { f.local.taskById(synced.id) != null }

        assertEquals(null, db.deletedTaskDao().byId(synced.id))
        assertEquals(emptyList(), f.remote.deletedTaskIds)
        assertEquals("on the server", f.local.taskById(synced.id)?.title)
    }

    /** An archive while signed out stands: its delete carries the archived task, and the web keeps the archive too. */
    @Test
    fun aTaskArchivedSignedOutStaysArchivedAfterTheNextSignIn() = runBlocking {
        val f = signedInFixture()
        signOutAndDrain(f)
        TaskStore(db, clock).setCompleted(synced.id, true)
        ArchiveStore(db, clock).archive(synced.id)
        assertTrue(db.archivedTaskDao().byId(synced.id) != null && queuedDeleteFor(synced.id), "the archive did not land")

        val before = f.coordinator.finishedRuns.value
        signInAgain(f)
        awaitRunAfter(f.coordinator, before)

        assertTrue(db.archivedTaskDao().byId(synced.id) != null, "the archive was undone")
        assertEquals(null, f.local.taskById(synced.id))
        assertEquals(emptyList(), f.remote.deletedTaskIds)
    }
}
