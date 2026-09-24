package dev.vinny.gsd.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.model.ReminderPlanner
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.theme.GsdColorsLight
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NotificationsTest {
    @get:org.junit.Rule
    val permission: androidx.test.rule.GrantPermissionRule =
        androidx.test.rule.GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun channelsExistAfterAppInit() {
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(Channels.REMINDERS))
        assertNotNull(manager.getNotificationChannel(Channels.REMINDERS_SILENT))
    }

    @Test
    fun reminderBroadcastPostsNotificationAndMarksSent(): Unit = runBlocking {
        val container = (context.applicationContext as GsdApp).container
        val task = container.store.add(
            Task(title = "standup", urgent = true, important = true,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        context.sendBroadcast(intent)

        val manager = context.getSystemService(NotificationManager::class.java)
        var posted = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (manager.activeNotifications.any { it.id == task.id.hashCode() }) {
                posted = true
                break
            }
            delay(100)
        }
        assertTrue(posted, "reminder notification never posted")
        assertTrue(container.store.get(task.id)?.notificationSent == true)
        manager.cancel(task.id.hashCode())
        container.store.delete(task.id)
    }

    @Test
    fun aFiredReminderStaysOutOfLastWriteWins(): Unit = runBlocking {
        val container = (context.applicationContext as GsdApp).container
        val task = container.store.add(
            Task(title = "review finding S2", urgent = true, important = true,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderScheduler.ACTION_REMINDER
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        context.sendBroadcast(intent)

        var sent = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (container.store.get(task.id)?.notificationSent == true) {
                sent = true
                break
            }
            delay(100)
        }
        assertTrue(sent, "the receiver never marked the reminder sent")
        // A reminder firing is bookkeeping, not an edit: a newer change from another device must still win
        assertEquals(task.updatedAt.toEpochMilli(), container.store.get(task.id)?.updatedAt?.toEpochMilli())
        assertEquals(1, container.db.syncQueueDao().countForTask(task.id)) // only the CREATE row from add()
        context.getSystemService(NotificationManager::class.java).cancel(task.id.hashCode())
        container.store.delete(task.id)
    }

    @Test
    fun completeActionCompletesTaskThroughStore(): Unit = runBlocking {
        val container = (context.applicationContext as GsdApp).container
        val task = container.store.add(
            Task(title = "notify me", urgent = false, important = false,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_COMPLETE
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        context.sendBroadcast(intent) // real broadcast so goAsync() has a PendingResult

        var completed = false
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (container.store.get(task.id)?.completed == true) {
                completed = true
                break
            }
            delay(100)
        }
        assertTrue(completed, "complete action never landed")
        container.store.delete(task.id) // leave the shared app db clean
    }

    /**
     * Review finding 5: the posted reminder sets notificationSent, and the Snooze button left it set.
     * The planner then refused the task, so "Snooze 1 hour" never fired again.
     */
    @Test
    fun aReminderSnoozedFromItsNotificationIsPlannedAgain(): Unit = runBlocking {
        val container = (context.applicationContext as GsdApp).container
        val now = Instant.now()
        val task = container.store.add(
            Task(title = "snooze me", urgent = true, important = true, createdAt = now, updatedAt = now,
                dueDate = now.plusSeconds(2 * 3600)),
        )
        container.store.markReminderSent(task.id, now) // what the receiver records when the reminder posts

        val snooze = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SNOOZE
            putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
        }
        context.sendBroadcast(snooze) // real broadcast so goAsync() has a PendingResult

        val deadline = System.currentTimeMillis() + 10_000
        while (container.store.get(task.id)?.snoozedUntil == null && System.currentTimeMillis() < deadline) delay(100)
        val snoozed = assertNotNull(container.store.get(task.id), "the task vanished")
        assertNotNull(snoozed.snoozedUntil, "snooze action never landed")
        assertTrue(snoozed.notificationSent, "the first reminder did post")

        val fireAt = ReminderPlanner.fireTime(
            snoozed, container.notificationSettingsStore.get(), Instant.now(), ZoneId.systemDefault(),
        )
        assertNotNull(fireAt, "a snoozed reminder has no fire time, so it never fires again")
        container.store.delete(task.id) // leave the shared app db clean
        container.trashStore.deleteForever(task.id)
    }

    /** Review finding 27: reminders used a stock system icon, with no color and no category. */
    @Test
    fun aReminderCarriesTheBrandIconColorAndCategory(): Unit = runBlocking {
        val container = (context.applicationContext as GsdApp).container
        org.junit.Assume.assumeFalse("signed in: refusing to write test tasks", container.syncCoordinator.signedIn.value)
        val task = container.store.add(
            Task(title = "finding 27", urgent = true, important = true, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH),
        )
        context.sendBroadcast(
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderScheduler.ACTION_REMINDER
                putExtra(ReminderScheduler.EXTRA_TASK_ID, task.id)
            },
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        var posted: android.app.Notification? = null
        val deadline = System.currentTimeMillis() + 10_000
        while (posted == null && System.currentTimeMillis() < deadline) {
            posted = manager.activeNotifications.firstOrNull { it.id == task.id.hashCode() }?.notification
            if (posted == null) delay(100)
        }
        manager.cancel(task.id.hashCode())
        container.store.delete(task.id)

        assertNotNull(posted, "reminder notification never posted")
        assertEquals(dev.vinny.gsd.R.drawable.ic_notification, posted.smallIcon.resId)
        assertEquals(android.app.Notification.CATEGORY_REMINDER, posted.category)
        assertEquals(GsdColorsLight.tint.toArgb(), posted.color)
    }
}
