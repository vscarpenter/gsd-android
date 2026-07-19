package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.FilterCriteria
import dev.vinny.gsd.model.FilterStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class SmartViewStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var views: SmartViewStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        views = SmartViewStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun seedingIsIdempotentAndBuiltInsImmutable() = runTest {
        views.seedBuiltInsIfEmpty()
        views.seedBuiltInsIfEmpty()
        val all = views.observeViews().first()
        assertEquals(9, all.size)
        assertFailsWith<SmartViewStore.BuiltInImmutable> { views.delete("today-focus") }
    }

    @Test
    fun customCrudRoundTrip() = runTest {
        views.seedBuiltInsIfEmpty()
        val custom = views.create("Deep work", "bolt", FilterCriteria(status = FilterStatus.ACTIVE, tags = listOf("deep")))
        assertEquals(12, custom.id.length)
        assertEquals(10, views.observeViews().first().size)
        views.update(custom.copy(name = "Deeper work"))
        assertEquals("Deeper work", views.observeViews().first().first { it.id == custom.id }.name)
        views.delete(custom.id)
        assertEquals(9, views.observeViews().first().size)
    }

    @Test
    fun pinCapAtFive() = runTest {
        views.setPinned(listOf("a", "b", "c", "d", "e"))
        assertEquals(listOf("a", "b", "c", "d", "e"), views.pinned())
        assertFailsWith<SmartViewStore.TooManyPins> {
            views.setPinned(listOf("a", "b", "c", "d", "e", "f"))
        }
    }

    @Test
    fun deletingPinnedViewUnpinsIt() = runTest {
        val custom = views.create("Pin me", "bolt", FilterCriteria())
        views.setPinned(listOf(custom.id))
        views.delete(custom.id)
        assertEquals(emptyList(), views.pinned())
    }
}
