package dev.vinny.gsd.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.auth.CachedTokenStore
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.SyncMetadataEntity
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.fail

private class NoTokens : TokenStore {
    override suspend fun token(): String? = null
    override suspend fun save(token: String) = Unit
    override suspend fun clear() = Unit
}

/** Review findings 16 and 42: nothing reaches cloud backup, and moved data gets its own device id. */
@RunWith(AndroidJUnit4::class)
class DeviceIdentityTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: GsdDatabase
    private lateinit var prefsFile: File
    private lateinit var marker: File
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(context)
        prefsFile = File.createTempFile("gsd-prefs", ".preferences_pb", context.cacheDir)
        marker = File(context.cacheDir, "device-identity-test").apply { delete() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
        prefsFile.delete()
        marker.delete()
    }

    /** A signed-out coordinator: start() only settles the device id and finds no one to sign in. */
    private fun coordinator(): SyncCoordinator {
        val tokens = CachedTokenStore(NoTokens())
        val clock = Clock.systemUTC()
        val api = PocketBaseApi("http://127.0.0.1:9", { null }) // never reached: nobody is signed in
        val local = RoomSyncLocalStore(db)
        return SyncCoordinator(
            context, SyncEngine(api, local, clock), AuthManager(api, tokens, clock), tokens, db, local,
            SseClient("http://127.0.0.1:9", { null }), scope,
            GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })),
            deviceMarker = marker,
        )
    }

    private suspend fun awaitMarker() {
        val deadline = System.currentTimeMillis() + 5_000
        while (!marker.exists()) {
            if (System.currentTimeMillis() > deadline) fail("the device marker was never written")
            delay(50)
        }
    }

    @Test
    fun dataFromAnotherPhoneTakesItsOwnDeviceId(): Unit = runBlocking {
        db.syncMetadataDao().upsert(SyncMetadataEntity(cursor = null, deviceId = "old-phone", deviceName = "Pixel"))

        coordinator().start()
        awaitMarker()

        assertNotEquals("old-phone", db.syncMetadataDao().get()?.deviceId)
    }

    @Test
    fun thisPhonesOwnIdSurvivesALaterLaunch(): Unit = runBlocking {
        db.syncMetadataDao().upsert(SyncMetadataEntity(cursor = null, deviceId = "this-phone", deviceName = "Pixel"))
        marker.createNewFile()

        coordinator().start()
        delay(500) // long enough for a wrong reset to have run

        assertEquals("this-phone", db.syncMetadataDao().get()?.deviceId)
    }

    @Test
    fun nothingGoesToCloudBackup() {
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
