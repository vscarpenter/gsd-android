package dev.vinny.gsd.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

private class MemoryTokenStore(var stored: String?) : TokenStore {
    override suspend fun token(): String? = stored
    override suspend fun save(token: String) { stored = token }
    override suspend fun clear() { stored = null }
}

/**
 * Answers the way the server does: the owner sees their task, and a request with no token is a
 * guest that gets an empty listing (the list rule filters, it does not reject).
 */
private class SessionRemote(private val token: () -> String?, private val task: Task) : SyncRemote {
    /** A token the server revoked before it expired: the app still holds it, the server rejects it. */
    @Volatile var tokenRevoked = false

    private fun guest() = token() == null || tokenRevoked

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val items = if (guest()) emptyList() else listOf(WireMapper.push(task, "other-device", "user-1", defaultReminder = 15))
        return TaskListPage(
            page = 1, totalPages = 1, items = items,
            updatedStamps = items.map { PbDates.formatSpace(task.updatedAt) },
        )
    }
    override suspend fun createTask(wire: WireTask): WireTask = wire
    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask = wire
    override suspend fun deleteTask(recordId: String) = Unit
    override suspend fun confirmIdentity(ownerId: String) {
        if (guest()) throw PocketBaseApi.Unauthorized(401)
    }
}

/** Review finding S1: a session that ends mid-process must sign the user out, never empty the device. */
@RunWith(AndroidJUnit4::class)
class SyncCoordinatorSessionTest {
    private lateinit var db: GsdDatabase
    private lateinit var prefsFile: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GsdDatabase.buildInMemory(context)
        prefsFile = File.createTempFile("gsd-prefs", ".preferences_pb", context.cacheDir)
        // A handler, so an exception that escapes the coordinator ends its loop (and the test times
        // out) instead of crashing the instrumentation process
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, _ -> })
    }

    @After
    fun tearDown() {
        // Wait for the coordinator's coroutines to finish before closing: a cancelled Room call
        // that lands on a closed database fails whichever test runs next.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        prefsFile.delete()
    }

    private suspend fun awaitSignedIn(coordinator: SyncCoordinator, expected: Boolean) {
        withTimeoutOrNull(5_000) { coordinator.signedIn.first { it == expected } }
            ?: fail("signedIn never became $expected")
    }

    private fun jwt(userId: String, expiresAt: Instant): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256"}""".toByteArray())
        val body = encoder.encodeToString("""{"id":"$userId","exp":${expiresAt.epochSecond}}""".toByteArray())
        return "$header.$body.sig"
    }

    private val clock: Clock = Clock.systemUTC()
    private val synced = Task(id = "synced-task", title = "keep me", urgent = true, important = true,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)
    // 30 days out: well past the 7-day refresh window, so nothing here touches the network
    private val healthyToken get() = jwt("user-1", clock.instant().plusSeconds(30L * 24 * 3600))

    private class Fixture(
        val coordinator: SyncCoordinator,
        val tokens: CachedTokenStore,
        val local: RoomSyncLocalStore,
        val remote: SessionRemote,
    )

    /** A signed-in coordinator whose one task is already on the server (no queue row). */
    private suspend fun signedInFixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokens = CachedTokenStore(MemoryTokenStore(healthyToken))
        val auth = AuthManager(PocketBaseApi("http://127.0.0.1:9", { tokens.cachedToken() }), tokens, clock)
        val local = RoomSyncLocalStore(db)
        val remote = SessionRemote({ tokens.cachedToken() }, synced)
        val engine = SyncEngine(remote, local, clock, throttleMs = 0, ownerId = { auth.currentUserId() })
        val coordinator = SyncCoordinator(
            context, engine, auth, tokens, db, local,
            SseClient("http://127.0.0.1:9", { tokens.cachedToken() }),
            scope, GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })),
        )
        local.upsertFromRemote(synced) // written the way a pull writes it
        coordinator.start()
        awaitSignedIn(coordinator, true)
        return Fixture(coordinator, tokens, local, remote)
    }

    /** What a definitive refresh rejection does (AuthManager.ensureFreshToken), followed by the next run. */
    private suspend fun expireSession(fixture: Fixture) {
        fixture.tokens.clear()
        fixture.coordinator.requestSync(true)
        awaitSignedIn(fixture.coordinator, false)
    }

    /** A pull's spinner waits on this count, so a run that ends any way at all has to move it (spec §6.1). */
    @Test
    fun aRunCountsWhenItFinishesAndWhenItEndsTheSession() = runBlocking {
        val fixture = signedInFixture()
        suspend fun awaitRunAfter(run: Long) {
            withTimeoutOrNull(5_000) { fixture.coordinator.finishedRuns.first { it > run } }
                ?: fail("finishedRuns never moved past $run")
        }

        val beforeClean = fixture.coordinator.finishedRuns.value
        fixture.coordinator.requestSync(true)
        awaitRunAfter(beforeClean)

        val beforeRejected = fixture.coordinator.finishedRuns.value
        fixture.remote.tokenRevoked = true
        fixture.coordinator.requestSync(true)
        awaitRunAfter(beforeRejected)
    }

    @Test
    fun aSessionThatEndsMidProcessSignsOutAndKeepsEveryTask() = runBlocking {
        val fixture = signedInFixture()

        expireSession(fixture)

        assertTrue(fixture.coordinator.sessionExpired.value)
        assertEquals(listOf("synced-task"), fixture.local.activeTasks().map { it.id })
    }

    @Test
    fun signingInAgainClearsTheExpiredNote() = runBlocking {
        val fixture = signedInFixture()
        expireSession(fixture)

        fixture.tokens.save(healthyToken)
        fixture.coordinator.confirmOwner(AuthResult(healthyToken, AuthRecord("user-1")), eraseLocalFirst = false)

        assertFalse(fixture.coordinator.sessionExpired.value)
    }

    /**
     * Review follow-up S1: a token the server revoked with more than 7 days left never reaches the
     * refresh, so the identity check's 401 must end the session. The token goes too, or a restart
     * would find it and sign back in to a dead session.
     */
    @Test
    fun aRevokedTokenSignsOutAndIsForgotten() = runBlocking {
        val fixture = signedInFixture()
        fixture.remote.tokenRevoked = true

        fixture.coordinator.requestSync(true)
        awaitSignedIn(fixture.coordinator, false)

        assertTrue(fixture.coordinator.sessionExpired.value)
        assertEquals(null, fixture.tokens.token())
        assertEquals(listOf("synced-task"), fixture.local.activeTasks().map { it.id })
    }

    /** Parity backlog B-46: the auto-archive sweep hangs off a clean sync, once its pull has landed. */
    @Test
    fun aCleanSyncRunsTheAfterSyncWork() = runBlocking {
        val fixture = signedInFixture()
        val ran = CompletableDeferred<Unit>()
        fixture.coordinator.afterCleanSync = { ran.complete(Unit) }

        fixture.coordinator.requestSync(true)

        withTimeoutOrNull(5_000) { ran.await() } ?: fail("afterCleanSync never ran")
    }

    /** A sweep that throws must not end the loop that runs every later sync. */
    @Test
    fun aFailingAfterSyncStepKeepsSyncing() = runBlocking {
        val fixture = signedInFixture()
        val calls = AtomicInteger()
        val later = CompletableDeferred<Unit>()
        fixture.coordinator.afterCleanSync = {
            if (calls.incrementAndGet() == 1) throw IllegalStateException("sweep failed")
            later.complete(Unit)
        }

        fixture.coordinator.requestSync(true)
        fixture.coordinator.requestSync(true)

        withTimeoutOrNull(5_000) { later.await() } ?: fail("syncing stopped after the step failed")
    }
}
