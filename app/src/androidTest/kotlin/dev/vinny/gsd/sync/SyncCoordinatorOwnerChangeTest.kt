package dev.vinny.gsd.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.prefs.PendingSignIn
import dev.vinny.gsd.store.GsdDatabase
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.fail

private class OwnerTokenStore(var stored: String?) : TokenStore {
    override suspend fun token(): String? = stored
    override suspend fun save(token: String) { stored = token }
    override suspend fun clear() { stored = null }
}

/**
 * One server, several accounts. A request sees only the rows of the signed-in user, and the
 * listing honors the pull cursor the way the `updated >= cursor` filter does.
 */
private class AccountsRemote : SyncRemote {
    private class Row(val wire: WireTask, val updated: Instant)

    private val rows = CopyOnWriteArrayList<Row>()
    private var nextId = 1

    @Volatile
    var sessionUser: String? = null

    fun seed(owner: String, task: Task, updated: Instant) {
        rows += Row(WireMapper.push(task, "other-device", owner, defaultReminder = 15).copy(id = "rec${nextId++}"), updated)
    }

    fun taskIdsOf(owner: String): List<String> = rows.filter { it.wire.owner == owner }.map { it.wire.taskId }

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val since = PbDates.parse(updatedCursor) ?: Instant.EPOCH
        val visible = rows.filter { it.wire.owner == sessionUser && !it.updated.isBefore(since) }
        return TaskListPage(
            page = 1, totalPages = 1, items = visible.map { it.wire },
            updatedStamps = visible.map { PbDates.formatSpace(it.updated) },
        )
    }

    override suspend fun createTask(wire: WireTask): WireTask {
        val stored = wire.copy(id = "rec${nextId++}")
        rows += Row(stored, Instant.now())
        return stored
    }

    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask {
        rows.removeAll { it.wire.id == recordId }
        rows += Row(wire.copy(id = recordId), Instant.now())
        return wire.copy(id = recordId)
    }

    override suspend fun deleteTask(recordId: String) {
        rows.removeAll { it.wire.id == recordId }
    }

    override suspend fun confirmIdentity(ownerId: String) {
        if (sessionUser != ownerId) throw PocketBaseApi.Unauthorized(401)
    }
}

/** Review finding S3: switching accounts must keep what the user chose to keep, and pull everything new. */
@RunWith(AndroidJUnit4::class)
class SyncCoordinatorOwnerChangeTest {
    private val clock: Clock = Clock.systemUTC()
    private lateinit var db: GsdDatabase
    private lateinit var prefsFile: File
    private lateinit var scope: CoroutineScope
    private lateinit var tokens: CachedTokenStore
    private lateinit var local: RoomSyncLocalStore
    private lateinit var remote: AccountsRemote
    /** One instance per test: DataStore refuses two active instances on the same file. */
    private lateinit var prefs: GsdPrefs
    private var lastOwner: String? = null

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GsdDatabase.buildInMemory(context)
        prefsFile = File.createTempFile("gsd-prefs", ".preferences_pb", context.cacheDir)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        local = RoomSyncLocalStore(db)
        remote = AccountsRemote()
        prefs = GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
    }

    @After
    fun tearDown() {
        // Wait for the coordinator's coroutines to finish before closing: a cancelled Room call
        // that lands on a closed database fails whichever test runs next.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        prefsFile.delete()
    }

    // 30 days out: well past the 7-day refresh window, so nothing here touches the network
    private fun jwt(userId: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val exp = clock.instant().plusSeconds(30L * 24 * 3600).epochSecond
        val body = encoder.encodeToString("""{"id":"$userId","exp":$exp}""".toByteArray())
        return "$header.$body.sig"
    }

    private fun task(id: String) = Task(
        id = id, title = id, urgent = true, important = true,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"), updatedAt = Instant.parse("2026-09-01T00:00:00Z"),
    )

    /** A coordinator as the app builds it, holding whatever token the "process" starts with. */
    private fun coordinator(startingToken: String?): SyncCoordinator {
        val context = ApplicationProvider.getApplicationContext<Context>()
        tokens = CachedTokenStore(OwnerTokenStore(startingToken))
        val auth = AuthManager(PocketBaseApi("http://127.0.0.1:9", { tokens.cachedToken() }), tokens, clock)
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { auth.currentUserId() })
        return SyncCoordinator(
            context, engine, auth, tokens, db, local,
            SseClient("http://127.0.0.1:9", { tokens.cachedToken() }),
            scope, prefs,
        ).also { built ->
            built.lastOwnerId = { lastOwner }
            built.onOwnerConfirmed = { result -> lastOwner = result.record.id }
        }
    }

    private suspend fun awaitUntil(what: String, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            delay(50)
        }
        fail("timed out waiting until $what")
    }

    /** Signed in as user-a with one full sync behind it. */
    private suspend fun signedInAsA(): SyncCoordinator {
        lastOwner = "user-a"
        remote.sessionUser = "user-a"
        val coordinator = coordinator(jwt("user-a"))
        coordinator.start()
        awaitUntil("the first sync as user-a finishes") { coordinator.lastResult.value != null }
        return coordinator
    }

    private suspend fun signOutThenSignInAsB(coordinator: SyncCoordinator, eraseLocalFirst: Boolean) {
        coordinator.signOut()
        val tokenB = jwt("user-b")
        tokens.save(tokenB) // AuthManager.completeOAuth saves the token before the prompt is answered
        remote.sessionUser = "user-b"
        coordinator.confirmOwner(AuthResult(tokenB, AuthRecord("user-b", "b@example.com")), eraseLocalFirst)
    }

    @Test
    fun keepMyTasksKeepsTheTaskAndUploadsItToTheNewAccount() = runBlocking {
        val kept = task("kept-task")
        remote.seed("user-a", kept, updated = Instant.parse("2026-09-01T00:00:05Z"))
        local.upsertFromRemote(kept) // already on account A: no queue row
        val coordinator = signedInAsA()

        signOutThenSignInAsB(coordinator, eraseLocalFirst = false)

        awaitUntil("the kept task reaches user-b's account") { "kept-task" in remote.taskIdsOf("user-b") }
        assertEquals(listOf("kept-task"), local.activeTasks().map { it.id })
        assertEquals(listOf("kept-task"), remote.taskIdsOf("user-a")) // the old account is untouched
    }

    @Test
    fun startFreshPullsTasksOlderThanTheOldAccountsCursor() = runBlocking {
        val recentOnA = task("recent-on-a")
        val oldOnB = task("old-on-b")
        remote.seed("user-a", recentOnA, updated = clock.instant().minusSeconds(60))
        remote.seed("user-b", oldOnB, updated = Instant.parse("2020-01-01T00:00:00Z"))
        val coordinator = signedInAsA()
        awaitUntil("user-a's pull advances the cursor") { local.cursor() != null }

        signOutThenSignInAsB(coordinator, eraseLocalFirst = true)

        awaitUntil("user-b's older task arrives") { local.activeTasks().any { it.id == "old-on-b" } }
        assertEquals(listOf("old-on-b"), local.activeTasks().map { it.id })
    }

    @Test
    fun anOwnerChangeThatDiedWithNoTasksLeftStillPullsTheNewAccountsOlderTasks() = runBlocking {
        remote.seed("user-b", task("old-on-b"), updated = Instant.parse("2020-01-01T00:00:00Z"))
        // The app saved user-b's token and died before it cleared user-a's cursor. No tasks are left,
        // so no prompt comes back. The state is built directly, because the erase now clears the cursor.
        lastOwner = "user-a"
        local.saveCursor(PbDates.formatSpace(clock.instant().minusSeconds(60)))
        remote.sessionUser = "user-b"

        coordinator(jwt("user-b")).start()

        awaitUntil("user-b's older task arrives") { local.activeTasks().any { it.id == "old-on-b" } }
        assertEquals("user-b", lastOwner)
    }

    @Test
    fun aFirstSignInThatDiedBeforeItsOwnerWasRecordedGetsOneOnTheNextLaunch() = runBlocking {
        // Without a recorded owner, a later switch to another account finds no previous owner and never asks.
        lastOwner = null
        remote.sessionUser = "user-a"

        coordinator(jwt("user-a")).start()

        awaitUntil("the owner is recorded") { lastOwner == "user-a" }
    }

    @Test
    fun anUnansweredPromptComesBackAfterARestartAndNothingSyncs() = runBlocking {
        val kept = task("kept-task")
        remote.seed("user-a", kept, updated = Instant.parse("2026-09-01T00:00:05Z"))
        local.upsertFromRemote(kept)
        // The app died with the "Different account" prompt open: user-b's token is saved, user-a is still the owner
        lastOwner = "user-a"
        remote.sessionUser = "user-b"
        val coordinator = coordinator(jwt("user-b"))

        coordinator.start()

        awaitUntil("the prompt is raised again") { coordinator.switchPrompt.value != null }
        assertEquals("user-b", coordinator.switchPrompt.value?.record?.id)
        assertFalse(coordinator.signedIn.value)
        delay(500) // long enough for a wrongly started sync to have run
        assertNull(coordinator.lastResult.value)
        assertEquals(listOf("kept-task"), local.activeTasks().map { it.id })
    }

    /** Review follow-up S3: a prompt rebuilt after a restart knew no email, so Settings showed none. */
    @Test
    fun aPromptRebuiltAfterARestartKeepsTheEmailAndProvider() = runBlocking {
        local.upsertFromRemote(task("kept-task"))
        lastOwner = "user-a"
        remote.sessionUser = "user-b"
        // What completeOAuthCallback saved before the app died with the prompt open
        prefs.setPendingSignIn(PendingSignIn("user-b", "b@example.com", "google"))
        val coordinator = coordinator(jwt("user-b"))
        var confirmedProvider: String? = null
        coordinator.onOwnerConfirmed = { result ->
            lastOwner = result.record.id
            confirmedProvider = coordinator.providerFor(result.record.id)
        }

        coordinator.start()
        awaitUntil("the prompt is raised again") { coordinator.switchPrompt.value != null }
        val rebuilt = coordinator.switchPrompt.value!!
        coordinator.confirmOwner(rebuilt, eraseLocalFirst = false)

        assertEquals("b@example.com", rebuilt.record.email)
        assertEquals("google", confirmedProvider)
        assertNull(prefs.pendingSignIn.first()) // answered, so nothing is pending any more
    }
}
