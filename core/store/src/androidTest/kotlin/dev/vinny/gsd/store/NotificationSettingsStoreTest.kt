package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class NotificationSettingsStoreTest {
    private val fixed = Clock.fixed(Instant.parse("2026-07-19T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var store: NotificationSettingsStore

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = NotificationSettingsStore(db, fixed)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun defaultsSynthesizedWhenRowAbsent() = runTest {
        val settings = store.observe().first()
        assertEquals(true, settings.enabled)
        assertEquals(15, settings.defaultReminderMinutes)
        assertEquals(true, settings.soundEnabled)
        assertEquals(null, settings.quietHoursStart)
        assertEquals(false, settings.permissionAsked)
    }

    @Test
    fun updateRoundTripsIncludingQuietHours() = runTest {
        store.update {
            it.copy(
                defaultReminderMinutes = 60,
                soundEnabled = false,
                quietHoursStart = LocalTime.of(22, 0),
                quietHoursEnd = LocalTime.of(7, 0),
            )
        }
        val settings = store.observe().first()
        assertEquals(60, settings.defaultReminderMinutes)
        assertEquals(false, settings.soundEnabled)
        assertEquals(LocalTime.of(22, 0), settings.quietHoursStart)
        assertEquals(LocalTime.of(7, 0), settings.quietHoursEnd)
    }

    @Test
    fun permissionAskedPersists() = runTest {
        store.markPermissionAsked()
        assertEquals(true, store.observe().first().permissionAsked)
    }
}
