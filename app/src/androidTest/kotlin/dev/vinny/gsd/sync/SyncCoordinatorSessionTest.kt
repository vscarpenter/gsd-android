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
    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val items = if (token() == null) emptyList() else listOf(WireMapper.push(task, "other-device", "user-1"))
        return TaskListPage(
            page = 1, totalPages = 1, items = items,
            updatedStamps = items.map { PbDates.formatSpace(task.updatedAt) },
        )
    }
    override suspend fun createTask(wire: WireTask): WireTask = wire
    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask = wire
    override suspend fun deleteTask(recordId: String) = Unit
    override suspend fun confirmIdentity(ownerId: String) {
        if (token() == null) throw PocketBaseApi.Unauthorized(401)
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
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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

    private class Fixture(val coordinator: SyncCoordinator, val tokens: CachedTokenStore, val local: RoomSyncLocalStore)

    /** A signed-in coordinator whose one task is already on the server (no queue row). */
    private suspend fun signedInFixture(): Fixture {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val tokens = CachedTokenStore(MemoryTokenStore(healthyToken))
        val auth = AuthManager(PocketBaseApi("http://127.0.0.1:9", { tokens.cachedToken() }), tokens, clock)
        val local = RoomSyncLocalStore(db)
        val engine = SyncEngine(
            SessionRemote({ tokens.cachedToken() }, synced), local, clock,
            throttleMs = 0, ownerId = { auth.currentUserId() },
        )
        val coordinator = SyncCoordinator(
            context, engine, auth, tokens, db, local,
            SseClient("http://127.0.0.1:9", { tokens.cachedToken() }),
            scope, GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })),
        )
        local.upsertFromRemote(synced) // written the way a pull writes it
        coordinator.start()
        awaitSignedIn(coordinator, true)
        return Fixture(coordinator, tokens, local)
    }

    /** What a definitive refresh rejection does (AuthManager.ensureFreshToken), followed by the next run. */
    private suspend fun expireSession(fixture: Fixture) {
        fixture.tokens.clear()
        fixture.coordinator.requestSync(true)
        awaitSignedIn(fixture.coordinator, false)
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
}
