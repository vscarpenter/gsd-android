package dev.vinny.gsd.settings

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.R
import dev.vinny.gsd.model.BackupEnvelope
import dev.vinny.gsd.model.Interchange
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.palette.OneShotRequest
import dev.vinny.gsd.store.BackupAssembler
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.ImportApplier
import dev.vinny.gsd.store.ImportMode
import dev.vinny.gsd.store.TaskStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Review findings 39 and 40: export and import report every outcome, an export truncates what it
 * overwrites, and a failure never escapes as a crash. Uses its own in-memory database.
 */
@RunWith(AndroidJUnit4::class)
class SettingsWorkTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private val messages = OneShotRequest<String>()
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, GsdDatabase::class.java).build()
        store = TaskStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun nextMessage(): String = withTimeout(5_000) {
        coroutineScope {
            val received = CompletableDeferred<String>()
            val consumer = launch { messages.consume { received.complete(it) } }
            received.await().also { consumer.cancel() }
        }
    }

    private fun fileWith(name: String, text: String): Uri =
        Uri.fromFile(File(context.cacheDir, name).apply { writeText(text) })

    private fun task(id: String, title: String) = Task(
        id = id, title = title, urgent = false, important = false,
        createdAt = clock.instant(), updatedAt = clock.instant(),
    )

    @Test
    fun anImportReportsWhatItImportedAndSkipped(): Unit = runBlocking {
        val envelope = BackupEnvelope(tasks = listOf(task("fits-a00001", "fine"), task("too-long001", "x".repeat(81))))
        val export = Interchange.export(envelope, clock.instant())

        importBackup(ImportApplier(db, store, clock), messages, context, fileWith("two.json", export), ImportMode.MERGE)

        assertEquals(context.getString(R.string.import_result, 1, 1), nextMessage())
    }

    @Test
    fun anUnreadableFileSaysSoInsteadOfCrashing(): Unit = runBlocking {
        val missing = Uri.fromFile(File(context.cacheDir, "not-there.json").apply { delete() })

        importBackup(ImportApplier(db, store, clock), messages, context, missing, ImportMode.MERGE)

        assertEquals(context.getString(R.string.import_failed), nextMessage())
    }

    @Test
    fun anOversizedFileNamesTheLimit(): Unit = runBlocking {
        val huge = fileWith("huge.json", " ".repeat(10 * 1024 * 1024 + 1) + "{}")

        importBackup(ImportApplier(db, store, clock), messages, context, huge, ImportMode.MERGE)

        assertEquals(context.getString(R.string.import_too_large), nextMessage())
    }

    /** Plain "w" leaves the rest of a longer file in place, so the old export ended in stale bytes. */
    @Test
    fun anExportOverALongerFileLeavesNoStaleTail(): Unit = runBlocking {
        store.add(task("keep-me0001", "keep me"))
        val target = fileWith("over.json", "x".repeat(50_000))

        exportBackup(BackupAssembler(db), messages, context, target)

        val written = File(target.path!!).readText()
        assertEquals(1, Interchange.parse(written).envelope.tasks.size)
    }

    @Test
    fun anExportThatCannotOpenItsTargetSaysSo(): Unit = runBlocking {
        val nowhere = Uri.parse("content://dev.vinny.gsd.no-such-provider/export.json")

        exportBackup(BackupAssembler(db), messages, context, nowhere)

        assertEquals(context.getString(R.string.export_failed), nextMessage())
    }
}
