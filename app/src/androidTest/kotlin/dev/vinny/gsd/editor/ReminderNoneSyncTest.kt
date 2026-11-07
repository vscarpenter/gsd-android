package dev.vinny.gsd.editor

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.NotificationSettingsStore
import dev.vinny.gsd.store.TaskStore
import dev.vinny.gsd.store.toEntity
import dev.vinny.gsd.sync.RoomSyncLocalStore
import dev.vinny.gsd.sync.SyncEngine
import dev.vinny.gsd.sync.SyncRemote
import dev.vinny.gsd.sync.TaskListPage
import dev.vinny.gsd.sync.WireTask
import dev.vinny.gsd.theme.GsdTheme
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/** Keeps what the engine pushes. Nothing is listed, so the pull is a no-op and the push creates the record. */
private class ReminderRecordingRemote : SyncRemote {
    val records = linkedMapOf<String, WireTask>() // taskId -> record
    private var nextId = 1

    override suspend fun listTasks(page: Int, updatedCursor: String): TaskListPage {
        val items = records.values.toList()
        return TaskListPage(page = 1, totalPages = 1, items = items, updatedStamps = items.map { "" })
    }
    override suspend fun createTask(wire: WireTask): WireTask =
        wire.copy(id = "rec${nextId++}").also { records[wire.taskId] = it }
    override suspend fun updateTask(recordId: String, wire: WireTask): WireTask =
        wire.copy(id = recordId).also { records[wire.taskId] = it }
    override suspend fun deleteTask(recordId: String) = Unit
    override suspend fun confirmIdentity(ownerId: String) = Unit
}

/**
 * B-36 and D-10, section 5 (spec name: `None turns the reminder off and syncs`). Android had no way to turn
 * off one task's reminder: its first row wrote a null offset, which fired the default here and reached the
 * server as 0. None now writes `notificationEnabled = false` and keeps the number, and both sync.
 */
class ReminderNoneSyncTest {
    @get:Rule
    val rule = createComposeRule()

    private val clock = Clock.systemUTC()
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var viewModel: TaskEditorViewModel

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, clock)
        viewModel = TaskEditorViewModel(store)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun noneTurnsTheReminderOffAndSyncs() {
        val due = Instant.now().plus(2, ChronoUnit.DAYS)
        val task = runBlocking {
            store.add(Task(title = "dentist", urgent = true, important = true,
                createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, dueDate = due, notifyBefore = 45))
        }
        rule.setContent { GsdTheme { TaskEditorSheet(viewModel, NotificationSettingsStore(db, clock), onDismiss = {}) } }
        viewModel.openExisting(task.id)
        rule.waitUntil { viewModel.draft.value != null }

        rule.onNodeWithTag("reminder_row").performScrollTo().performClick()
        rule.onNode(hasText("None").and(hasAnyAncestor(isPopup()))).performClick()
        rule.onNodeWithTag("reminder_row").assertTextEquals("None")
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil { runBlocking { store.get(task.id)?.notificationEnabled == false } }

        val saved = runBlocking { store.get(task.id)!! }
        assertEquals(45, saved.notifyBefore) // off keeps the number, so turning it back on restores it
        val remote = ReminderRecordingRemote()
        val result = runBlocking {
            SyncEngine(remote, RoomSyncLocalStore(db), clock, throttleMs = 0, ownerId = { "user-1" }).runSync(false)
        }
        assertEquals("success", result.status)
        val pushed = remote.records.getValue(task.id)
        assertEquals(false, pushed.notificationEnabled)
        assertEquals(45, pushed.notifyBefore)
    }

    /**
     * D-10, sections 4 and 5, through the real sheet. A legacy null shows this device's default, here 60, not the
     * seeded 15. Picking that same number isn't a moved reminder, so the one already posted stays posted.
     */
    @Test
    fun pickingTheDefaultForALegacyNullKeepsASentReminder() {
        val settings = NotificationSettingsStore(db, clock)
        val posted = Instant.now().minus(1, ChronoUnit.HOURS)
        val legacy = Task(title = "standup", urgent = true, important = true, createdAt = posted, updatedAt = posted,
            dueDate = Instant.now().plus(2, ChronoUnit.DAYS), notifyBefore = null,
            notificationSent = true, lastNotificationAt = posted)
        runBlocking {
            settings.update { it.copy(defaultReminderMinutes = 60) }
            db.taskDao().upsert(legacy.toEntity()) // as a build before D-10 stored it
        }
        rule.setContent { GsdTheme { TaskEditorSheet(viewModel, settings, onDismiss = {}) } }
        viewModel.openExisting(legacy.id)
        rule.waitUntil { viewModel.draft.value != null }

        // The sheet reads the default from Room, so the label settles a moment after the first frame
        rule.waitUntil { runCatching { rule.onNodeWithTag("reminder_row").assertTextEquals("1 hour before") }.isSuccess }
        rule.onNodeWithTag("reminder_row").performScrollTo().performClick()
        rule.onNode(hasText("1 hour before").and(hasAnyAncestor(isPopup()))).performClick()
        rule.onNodeWithTag("reminder_row").assertTextEquals("1 hour before") // waits out the closing popup
        rule.onNodeWithText("Save").performClick()
        rule.waitUntil { runBlocking { store.get(legacy.id)?.notifyBefore != null } }

        val saved = runBlocking { store.get(legacy.id)!! }
        assertEquals(60, saved.notifyBefore)
        assertEquals(true, saved.notificationSent)
    }
}
