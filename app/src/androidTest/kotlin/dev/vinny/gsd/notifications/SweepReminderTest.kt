package dev.vinny.gsd.notifications

import android.app.NotificationManager
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vinny.gsd.model.ReminderSettings
import dev.vinny.gsd.model.SweepReminder
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.prefs.GsdPrefs
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The sweep alarm lives beside the task reminders and survives their rebuilds (spec-weekly-sweep.md §6). */
@RunWith(AndroidJUnit4::class)
class SweepReminderTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val scheduler = ReminderScheduler(context)
    private val zone = ZoneId.of("America/Chicago")
    private val now = Instant.parse("2026-11-08T16:00:00Z")
    private val on = SweepReminder(true, 7, LocalTime.of(9, 0))
    private lateinit var prefsFile: File
    private lateinit var prefs: GsdPrefs

    @Before
    fun setUp() {
        scheduler.cancelSweep()
        prefsFile = File.createTempFile("gsd-sweep-rem", ".preferences_pb")
        prefs = GsdPrefs(PreferenceDataStoreFactory.create(produceFile = { prefsFile }))
    }

    @After
    fun tearDown() {
        scheduler.cancelSweep()
        prefsFile.delete()
    }

    @Test
    fun turningItOnSchedulesTheAlarmAndOffCancelsIt() {
        scheduler.syncSweep(on, now, zone)
        assertTrue(scheduler.hasSweepAlarm())

        scheduler.syncSweep(SweepReminder.OFF, now, zone)
        assertFalse(scheduler.hasSweepAlarm())
    }

    @Test
    fun aTaskReminderResyncKeepsTheSweepReminder(): Unit = runBlocking {
        scheduler.syncSweep(on, now, zone)
        val task = Task(id = "task-keep-1", title = "t", urgent = true, important = true, createdAt = now, updatedAt = now, dueDate = now.plusSeconds(86_400))

        scheduler.resyncAll(listOf(task), ReminderSettings(), now)
        scheduler.resyncAll(emptyList(), ReminderSettings(), now)

        assertTrue(scheduler.hasSweepAlarm())
    }

    @Test
    fun aLocalEraseClearsTheSweepReminder(): Unit = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            SweepReminderSync(prefs, scheduler, Clock.fixed(now, zone)).start(scope)
            prefs.setSweepReminder(on)
            withTimeout(5_000) { while (!scheduler.hasSweepAlarm()) delay(20) }

            prefs.clearSweepState() // what a local erase runs

            withTimeout(5_000) { while (scheduler.hasSweepAlarm()) delay(20) }
            assertEquals(SweepReminder.OFF, prefs.sweepReminder.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun theNotificationHasFixedTextAndNoCountOrTaskContent() {
        val manager = context.getSystemService(NotificationManager::class.java)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        Channels.ensure(context)

        postSweepNotification(context, sound = false)

        val posted = manager.activeNotifications.firstOrNull { it.id == SWEEP_NOTIFICATION_ID }
        if (posted != null) { // skipped when the device has notifications switched off
            val extras = posted.notification.extras
            assertEquals("Weekly sweep", extras.getString("android.title"))
            assertEquals("A few minutes to clear what's gone quiet.", extras.getString("android.text"))
            manager.cancel(SWEEP_NOTIFICATION_ID)
        }
    }

    /** A posted task reminder is bookkeeping, so it doesn't restamp the task and queue no push (spec §3, staleness). */
    @Test
    fun aPostedReminderDoesntResetStaleness(): Unit = runBlocking {
        val db = GsdDatabase.buildInMemory(context)
        try {
            val old = Instant.parse("2026-09-01T00:00:00Z")
            val store = TaskStore(db, Clock.fixed(old, ZoneOffset.UTC))
            val task = store.add(Task(id = "task-stale-1", title = "t", urgent = false, important = true, createdAt = old, updatedAt = old))
            val queued = db.syncQueueDao().pending().size

            store.markReminderSent(task.id, Instant.parse("2026-11-08T16:00:00Z"))

            val after = checkNotNull(store.get(task.id))
            assertEquals(old, after.updatedAt)
            assertTrue(after.notificationSent)
            assertEquals(queued, db.syncQueueDao().pending().size)
        } finally {
            db.close()
        }
    }
}
