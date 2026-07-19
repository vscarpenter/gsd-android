package dev.vinny.gsd.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.GsdApp
import dev.vinny.gsd.model.Task
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
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
}
