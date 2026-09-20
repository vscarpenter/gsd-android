package dev.vinny.gsd.matrix

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Quadrant
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
import dev.vinny.gsd.sync.RoomSyncLocalStore
import java.io.File
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Review finding S6: the view model actions catch nothing, so a rejected write crashed the app.
 * These two went through validating store calls. A synced task over the limit must survive them.
 */
@RunWith(AndroidJUnit4::class)
class MatrixViewModelOverLimitTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var prefsFile: File
    private lateinit var viewModel: MatrixViewModel

    /** 80 characters on iOS. The emoji is two UTF-16 units, so Android sees 81. */
    private val pulled = Task(
        title = "x".repeat(79) + "👍", urgent = true, important = true, createdAt = now, updatedAt = now,
    )

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = GsdDatabase.buildInMemory(context)
        store = TaskStore(db, Clock.systemUTC())
        prefsFile = File.createTempFile("gsd-prefs", ".preferences_pb", context.cacheDir)
        viewModel = MatrixViewModel(
            store, GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })), TrashStore(db, Clock.systemUTC()),
        )
        RoomSyncLocalStore(db).upsertFromRemote(pulled) // pull stores a task without validating it
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    @Test
    fun itCanBeMovedToAnotherQuadrant() = runBlocking {
        viewModel.moveToQuadrant(pulled.id, Quadrant.ELIMINATE)

        awaitUntil("the task moves") { store.get(pulled.id)?.quadrant == Quadrant.ELIMINATE }
    }

    @Test
    fun itCanBeDuplicated() = runBlocking {
        viewModel.duplicate(pulled.id)

        awaitUntil("the copy lands") { store.observeTasks().first().size == 2 }
        assertEquals(listOf(pulled.title, pulled.title), store.observeTasks().first().map { it.title })
    }

    private suspend fun awaitUntil(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(5_000) { while (!condition()) delay(25) }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("Timed out waiting until $what", timeout)
        }
    }
}
