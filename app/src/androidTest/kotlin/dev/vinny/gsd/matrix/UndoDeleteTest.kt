package dev.vinny.gsd.matrix

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class UndoDeleteTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private var now: Instant = t0

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, object : Clock() {
            override fun instant(): Instant = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
        })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deleteReturnsSnapshotAndUndoRecreatesWithFreshUpdatedAt() = runTest {
        val added = store.add(Task(title = "keep me", urgent = false, important = false,
            createdAt = t0, updatedAt = t0))
        now = t0.plus(Duration.ofMinutes(1))
        val snapshot = store.delete(added.id)
        assertNotNull(snapshot)
        assertEquals(0, store.observeTasks().first().size)

        // Undo = re-create as a fresh write (spec §8.20): same id, newer updatedAt → LWW-safe
        now = t0.plus(Duration.ofMinutes(2))
        val restored = store.add(snapshot)
        assertEquals(added.id, restored.id)
        assertEquals("keep me", restored.title)
        assertTrue(restored.updatedAt.isAfter(snapshot.updatedAt))
        assertEquals(1, store.observeTasks().first().size)
    }
}
