package dev.vinny.gsd.notifications

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Review follow-up S6: a notification button ran its store write with no catch, so a database error
 * crashed the process. The write now reports failure, and the receiver leaves the notification up.
 * Uses its own in-memory database, so it never writes through the app's store.
 */
@RunWith(AndroidJUnit4::class)
class NotificationActionTest {
    private val now = Instant.parse("2026-07-19T12:00:00.000Z")

    private fun newDb(): GsdDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), GsdDatabase::class.java).build()

    @Test
    fun aFailedWriteIsReportedAndNotThrown(): Unit = runBlocking {
        val db = newDb()
        val store = TaskStore(db, Clock.fixed(now, ZoneOffset.UTC))
        // Room reopens a closed database on the next query, so close() would not break it. A missing table does.
        db.openHelper.writableDatabase.execSQL("DROP TABLE tasks")

        val done = runNotificationAction(NotificationActionReceiver.ACTION_COMPLETE, "gone-task", store, now)

        assertFalse(done)
        db.close()
    }

    @Test
    fun completeWritesThroughTheStore(): Unit = runBlocking {
        val db = newDb()
        val store = TaskStore(db, Clock.fixed(now, ZoneOffset.UTC))
        val task = store.add(Task(title = "t", urgent = true, important = true, createdAt = now, updatedAt = now))

        val done = runNotificationAction(NotificationActionReceiver.ACTION_COMPLETE, task.id, store, now)

        assertTrue(done)
        assertEquals(true, store.get(task.id)?.completed)
        db.close()
    }

    @Test
    fun snoozeWritesAnHourFromNow(): Unit = runBlocking {
        val db = newDb()
        val store = TaskStore(db, Clock.fixed(now, ZoneOffset.UTC))
        val task = store.add(Task(title = "t", urgent = true, important = true, createdAt = now, updatedAt = now))

        runNotificationAction(NotificationActionReceiver.ACTION_SNOOZE, task.id, store, now)

        assertEquals(now.plusSeconds(3600), store.get(task.id)?.snoozedUntil)
        db.close()
    }
}
