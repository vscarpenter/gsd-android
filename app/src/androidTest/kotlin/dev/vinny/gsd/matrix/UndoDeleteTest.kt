package dev.vinny.gsd.matrix

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.TrashStore
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
    private lateinit var trashStore: TrashStore
    private var now: Instant = t0

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        val clock = object : Clock() {
            override fun instant(): Instant = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
        }
        store = TaskStore(db, clock)
        trashStore = TrashStore(db, clock)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deleteMovesToTrashAndUndoRestoresWithOriginalTimestamps() = runTest {
        val added = store.add(Task(title = "keep me", urgent = false, important = false,
            createdAt = t0, updatedAt = t0))
        now = t0.plus(Duration.ofMinutes(1))
        val snapshot = store.delete(added.id)
        assertNotNull(snapshot)
        assertEquals(0, store.observeTasks().first().size)
        assertEquals(1, trashStore.observeTrash().first().size)

        // Undo = restore from trash (parity spec P§3.1): faithful undo, original timestamps preserved
        now = t0.plus(Duration.ofMinutes(2))
        val restored = trashStore.restore(added.id)
        assertNotNull(restored)
        assertEquals(added.id, restored.id)
        assertEquals("keep me", restored.title)
        assertEquals(snapshot.updatedAt, restored.updatedAt)

        // Task active again and trash empty after undo
        assertEquals(1, store.observeTasks().first().size)
        assertTrue(trashStore.observeTrash().first().isEmpty())
    }
}
