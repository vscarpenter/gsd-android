package dev.vinny.gsd.matrix

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.model.TopThree
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The four writes behind the Today section (spec-top-three.md §3.2). They read and write the pick
 * columns, so this runs against AND-C4's Room schema (version 5).
 */
@RunWith(AndroidJUnit4::class)
class TodayViewModelTest {
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var viewModel: TodayViewModel
    private lateinit var prefsFile: File
    private val zone = ZoneId.of("America/Chicago")
    private val clock = Clock.fixed(Instant.parse("2026-11-04T16:00:00Z"), zone)
    private val created = Instant.parse("2026-10-01T00:00:00Z")

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, Clock.fixed(Instant.parse("2026-11-04T16:30:00Z"), ZoneOffset.UTC))
        prefsFile = File.createTempFile("gsd-today", ".preferences_pb")
        viewModel = TodayViewModel(store, GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile })), clock)
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    private suspend fun add(id: String, urgent: Boolean = true): Task =
        store.add(Task(id = id, title = "Task $id", urgent = urgent, important = true, createdAt = created, updatedAt = created))

    private suspend fun settle() = withTimeout(5_000) { viewModel.view.first { it != null } }

    private suspend fun task(id: String) = checkNotNull(store.get(id))

    @Test
    fun addToTodayWritesTheDateAndTheNextRankAndNothingElse(): Unit = runBlocking {
        val before = add("task-a")

        viewModel.addToToday("task-a")
        withTimeout(5_000) { while (task("task-a").focusRank == null) kotlinx.coroutines.delay(20) }

        val after = task("task-a")
        assertEquals("2026-11-04", after.focusDate)
        assertEquals(1, after.focusRank)
        assertEquals(before.copy(focusDate = "2026-11-04", focusRank = 1, updatedAt = after.updatedAt), after)
    }

    @Test
    fun addToTodayQueuesAPush(): Unit = runBlocking {
        add("task-a")
        val queuedBefore = db.syncQueueDao().pending().size

        viewModel.addToToday("task-a")
        withTimeout(5_000) { while (task("task-a").focusRank == null) kotlinx.coroutines.delay(20) }

        assertEquals(queuedBefore + 1, db.syncQueueDao().pending().size)
    }

    @Test
    fun addToTodayIsRefusedWhenTodayIsFull(): Unit = runBlocking {
        listOf("task-a", "task-b", "task-c", "task-d").forEach { add(it) }
        listOf("task-a", "task-b", "task-c").forEach { viewModel.addToToday(it); withTimeout(5_000) { while (task(it).focusRank == null) kotlinx.coroutines.delay(20) } }

        viewModel.addToToday("task-d")
        kotlinx.coroutines.delay(300)

        assertNull(task("task-d").focusRank)
    }

    @Test
    fun removeClearsBothFields(): Unit = runBlocking {
        add("task-a")
        viewModel.addToToday("task-a")
        withTimeout(5_000) { while (task("task-a").focusRank == null) kotlinx.coroutines.delay(20) }

        viewModel.removeFromToday("task-a")
        withTimeout(5_000) { while (task("task-a").focusRank != null) kotlinx.coroutines.delay(20) }

        assertNull(task("task-a").focusDate)
    }

    @Test
    fun moveDownSwapsTwoRanks(): Unit = runBlocking {
        add("task-a"); add("task-b")
        viewModel.addToToday("task-a")
        withTimeout(5_000) { while (task("task-a").focusRank == null) kotlinx.coroutines.delay(20) }
        viewModel.addToToday("task-b")
        withTimeout(5_000) { while (task("task-b").focusRank == null) kotlinx.coroutines.delay(20) }

        viewModel.move("task-a", TopThree.Move.DOWN)
        withTimeout(5_000) { while (task("task-a").focusRank != 2) kotlinx.coroutines.delay(20) }

        assertEquals(1, task("task-b").focusRank)
    }

    @Test
    fun theViewFollowsTheStore(): Unit = runBlocking {
        add("task-a")
        viewModel.addToToday("task-a")
        withTimeout(5_000) { viewModel.view.first { it?.picks?.map { t -> t.id } == listOf("task-a") } }
        settle()
    }
}
