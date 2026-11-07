package dev.vinny.gsd.sweep

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.SweepReason
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The sweep session (spec-weekly-sweep.md §4, §10 item 3) against a real store. */
@RunWith(AndroidJUnit4::class)
class SweepViewModelTest {
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var prefs: GsdPrefs
    private lateinit var prefsFile: File
    private val zone = ZoneId.of("America/Chicago")
    private val sweepDay = Instant.parse("2026-11-08T16:00:00Z") // Sunday 10:00 in Chicago
    private val clock = Clock.fixed(sweepDay, zone)
    private val long_ago = Instant.parse("2026-09-01T00:00:00Z")

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        // The store stamps edits from its own clock, so an edit shows as a change in updatedAt
        store = TaskStore(db, Clock.fixed(Instant.parse("2026-11-08T16:30:00Z"), ZoneOffset.UTC))
        prefsFile = File.createTempFile("gsd-sweep", ".preferences_pb")
        prefs = GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
    }

    @After
    fun tearDown() {
        db.close()
        prefsFile.delete()
    }

    /** A fresh Do First task with no date, stamped at the store's own clock, so it has no card. */
    private suspend fun add(id: String): Task =
        store.add(Task(id = id, title = "Task $id", urgent = true, important = true, createdAt = sweepDay, updatedAt = sweepDay))

    /** An old row, stamped at [long_ago], so it's stale and has a card. */
    private suspend fun seedOld(id: String, due: Instant? = null) {
        TaskStore(db, Clock.fixed(long_ago, ZoneOffset.UTC))
            .add(Task(id = id, title = "Task $id", urgent = false, important = true, createdAt = long_ago, updatedAt = long_ago, dueDate = due))
    }

    private fun viewModel() = SweepViewModel(store, prefs, clock)

    private suspend fun SweepViewModel.awaitCard(): SweepState.Card =
        withTimeout(5_000) { state.first { it is SweepState.Card } } as SweepState.Card

    private suspend fun SweepViewModel.awaitEnd(): SweepState =
        withTimeout(5_000) { state.first { it is SweepState.End } }

    @Test
    fun openingSkippingAndLeavingWriteNothingToAnyTask(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z")) // overdue
        seedOld("task-b")
        val before = store.observeTasks().first()
        val queuedBefore = db.syncQueueDao().pending().size
        val vm = viewModel()

        vm.awaitCard()
        vm.skip()
        vm.awaitCard()
        // Leaving here, with a card still showing, is the Back button: nothing else happens

        assertEquals(before, store.observeTasks().first())
        assertEquals(queuedBefore, db.syncQueueDao().pending().size)
        assertNull(prefs.lastSweptAt.first())
    }

    @Test
    fun reachingTheEndStampsLastSwept(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        val vm = viewModel()
        vm.awaitCard()

        vm.skip()
        vm.awaitEnd()

        assertEquals(sweepDay, prefs.lastSweptAt.first())
    }

    @Test
    fun doneStampsLastSweptAndLeaves(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        seedOld("task-b")
        val vm = viewModel()
        vm.awaitCard()
        var left = false

        vm.done { left = true }
        withTimeout(5_000) { while (!left) kotlinx.coroutines.delay(20) }

        assertEquals(sweepDay, prefs.lastSweptAt.first())
    }

    @Test
    fun aSweepWithNoCardsEndsAtOnceAndStampsNothing(): Unit = runBlocking {
        add("task-a") // fresh, so no card
        val vm = viewModel()

        vm.awaitEnd()

        assertNull(prefs.lastSweptAt.first())
    }

    @Test
    fun aTaskCompletedElsewhereMidSweepIsSkippedWithoutAWord(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        seedOld("task-b", due = Instant.parse("2026-11-02T06:00:00Z"))
        seedOld("task-c", due = Instant.parse("2026-11-03T06:00:00Z"))
        val vm = viewModel()
        assertEquals("task-a", vm.awaitCard().task.id)

        store.setCompleted("task-b", true) // another device finished it
        vm.skip()

        val card = withTimeout(5_000) { vm.state.first { it is SweepState.Card && it.task.id != "task-a" } } as SweepState.Card
        assertEquals("task-c", card.task.id)
        assertEquals(3, card.position) // the position still counts the cards computed at open
        assertEquals(3, card.total)
    }

    @Test
    fun completeUsesTheExistingPathAndMovesOn(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        seedOld("task-b", due = Instant.parse("2026-11-02T06:00:00Z"))
        val vm = viewModel()
        vm.awaitCard()

        vm.complete("task-a")

        assertEquals("task-b", withTimeout(5_000) { vm.state.first { it is SweepState.Card && it.task.id == "task-b" } }.let { (it as SweepState.Card).task.id })
        assertEquals(true, store.get("task-a")?.completed)
    }

    @Test
    fun theCardShowsItsReason(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        val card = viewModel().awaitCard()
        assertEquals(SweepReason.OVERDUE, card.reason)
        assertEquals(1, card.position)
        assertEquals(1, card.total)
    }

    @Test
    fun theEndOffersTopThreeOnlyWhileTodayHasAFreeSlot(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        val open = viewModel()
        open.awaitCard()
        open.skip()
        open.awaitEnd()
        assertEquals(true, withTimeout(5_000) { open.offerTopThree.first() })

        listOf("task-p1", "task-p2", "task-p3").forEachIndexed { i, id ->
            val t = store.add(Task(id = id, title = id, urgent = true, important = true, createdAt = sweepDay, updatedAt = sweepDay))
            store.change(t.id) { it.copy(focusDate = "2026-11-08", focusRank = i + 1) }
        }
        val full = viewModel()
        full.awaitCard()
        full.skip()
        assertIs<SweepState.End>(full.awaitEnd())
        assertEquals(false, withTimeout(5_000) { full.offerTopThree.first() })
    }

    @Test
    fun aFailedWriteLeavesTheCardInPlaceAndReportsIt(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        seedOld("task-b", due = Instant.parse("2026-11-02T06:00:00Z"))
        val vm = viewModel()
        assertEquals("task-a", vm.awaitCard().task.id)
        val failures = mutableListOf<Unit>()
        val collector = kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Default) { vm.failures.collect { failures += it } }
        kotlinx.coroutines.delay(100)

        vm.advanceAfter { false }
        withTimeout(5_000) { while (failures.isEmpty()) kotlinx.coroutines.delay(20) }

        assertEquals("task-a", (vm.state.value as SweepState.Card).task.id)
        vm.advanceAfter { throw IllegalStateException("write failed") }
        withTimeout(5_000) { while (failures.size < 2) kotlinx.coroutines.delay(20) }
        assertEquals("task-a", (vm.state.value as SweepState.Card).task.id)

        vm.advanceAfter { true }
        withTimeout(5_000) { vm.state.first { it is SweepState.Card && it.task.id == "task-b" } }
        collector.cancel()
    }

    /** The end can stay on screen while picks change elsewhere, so the offer reads today's slot as it is now. */
    @Test
    fun theOfferFollowsTodaysSlotWhileTheEndIsShowing(): Unit = runBlocking {
        seedOld("task-a", due = Instant.parse("2026-11-01T06:00:00Z"))
        val vm = viewModel()
        vm.awaitCard()
        vm.skip()
        vm.awaitEnd()
        assertEquals(true, withTimeout(5_000) { vm.offerTopThree.first() })

        listOf("task-p1", "task-p2", "task-p3").forEachIndexed { i, id ->
            val t = store.add(Task(id = id, title = id, urgent = true, important = true, createdAt = sweepDay, updatedAt = sweepDay))
            store.change(t.id) { it.copy(focusDate = "2026-11-08", focusRank = i + 1) }
        }

        withTimeout(5_000) { vm.offerTopThree.first { !it } }
    }
}
