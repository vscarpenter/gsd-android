package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.ArchivedTask
import dev.vinny.gsd.model.BackupAppPreferences
import dev.vinny.gsd.model.BackupArchiveSettings
import dev.vinny.gsd.model.BackupEnvelope
import dev.vinny.gsd.model.BackupImport
import dev.vinny.gsd.model.BackupNotificationSettings
import dev.vinny.gsd.model.DeletedTask
import dev.vinny.gsd.model.FilterCriteria
import dev.vinny.gsd.model.SmartView
import dev.vinny.gsd.model.Task
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Round-trips the seven-store backup envelope through [BackupAssembler] and
 * [ImportApplier] (P§2.1/§2.2): export shape, replace-mode present-empty vs
 * absent-key semantics, merge-mode skip-on-collision, the singleton
 * merge-never-adopts rule, sync-queue silence for the six non-task stores,
 * and the one-store-per-id invariant.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val fixed = Clock.fixed(Instant.parse("2026-08-29T12:00:00.000Z"), ZoneOffset.UTC)
    private lateinit var db: GsdDatabase
    private lateinit var taskStore: TaskStore
    private lateinit var archiveStore: ArchiveStore
    private lateinit var smartViewStore: SmartViewStore
    private lateinit var assembler: BackupAssembler
    private lateinit var applier: ImportApplier

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        taskStore = TaskStore(db, fixed)
        archiveStore = ArchiveStore(db, fixed)
        smartViewStore = SmartViewStore(db, fixed)
        assembler = BackupAssembler(db)
        applier = ImportApplier(db, taskStore, fixed)
    }

    @After
    fun tearDown() = db.close()

    private fun draft(id: String, title: String = "t") =
        Task(id = id, title = title, urgent = false, important = false,
            createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH)

    @Test
    fun assemble_exportsCustomViewsOnly_andAllSingletons() = runTest {
        smartViewStore.seedBuiltInsIfEmpty()
        smartViewStore.create("Custom", "star", FilterCriteria())

        db.notificationSettingsDao().upsert(NotificationSettingsEntity(updatedAt = fixed.millis()))
        db.archiveSettingsDao().upsert(ArchiveSettingsEntity(enabled = true, archiveAfterDays = 45))
        db.appPreferencesDao().upsert(AppPreferencesEntity(pinnedSmartViewIdsJson = "[]"))

        val archived = taskStore.add(draft("archived-001", "archived"))
        taskStore.setCompleted(archived.id, true)
        archiveStore.archive(archived.id)

        val trashed = taskStore.add(draft("trashed-001", "trashed"))
        taskStore.delete(trashed.id)

        taskStore.add(draft("active-0001", "a1"))
        taskStore.add(draft("active-0002", "a2"))

        val envelope = assembler.assemble()

        assertEquals(2, envelope.tasks.size)
        assertEquals(1, envelope.archivedTasks!!.size)
        assertEquals(1, envelope.deletedTasks!!.size)
        assertEquals(1, envelope.smartViews!!.size) // built-ins excluded
        assertNotNull(envelope.notificationSettings)
        assertNotNull(envelope.archiveSettings)
        assertNotNull(envelope.appPreferences)
    }

    @Test
    fun replaceMode_presentEmptyArrayClearsStore_absentKeyLeavesIt() = runTest {
        val archived = taskStore.add(draft("archive-r001", "r"))
        taskStore.setCompleted(archived.id, true)
        archiveStore.archive(archived.id)
        assertEquals(1, db.archivedTaskDao().allIds().size)

        applier.apply(
            BackupImport(BackupEnvelope(tasks = emptyList(), archivedTasks = emptyList()), "2.1.0", 0),
            ImportMode.REPLACE,
        )
        assertTrue(db.archivedTaskDao().allIds().isEmpty())

        val reseeded = taskStore.add(draft("archive-r002", "r2"))
        taskStore.setCompleted(reseeded.id, true)
        archiveStore.archive(reseeded.id)
        assertEquals(1, db.archivedTaskDao().allIds().size)

        applier.apply(
            BackupImport(BackupEnvelope(tasks = emptyList(), archivedTasks = null), "2.1.0", 0),
            ImportMode.REPLACE,
        )
        assertEquals(1, db.archivedTaskDao().allIds().size)
    }

    @Test
    fun mergeMode_existingArchiveRowWinsOnCollision() = runTest {
        val local = taskStore.add(draft("collide-a01", "local"))
        taskStore.setCompleted(local.id, true)
        archiveStore.archive(local.id)

        val imported = ArchivedTask(task = draft(local.id, "imported"), archivedAt = fixed.instant())
        applier.apply(
            BackupImport(BackupEnvelope(tasks = emptyList(), archivedTasks = listOf(imported)), "2.1.0", 0),
            ImportMode.MERGE,
        )

        assertEquals("local", db.archivedTaskDao().byId(local.id)?.title)
    }

    @Test
    fun mergeMode_neverAdoptsSingletons() = runTest {
        db.notificationSettingsDao().upsert(
            NotificationSettingsEntity(
                enabled = true, defaultReminder = 15, soundEnabled = true,
                quietHoursStart = null, quietHoursEnd = null, permissionAsked = false,
                updatedAt = 1_000L,
            ),
        )
        val imported = BackupNotificationSettings(
            enabled = false, defaultReminder = 45, soundEnabled = false,
            quietHoursStart = "22:00", quietHoursEnd = "07:00",
            permissionAsked = true, updatedAt = fixed.instant(),
        )

        applier.apply(
            BackupImport(BackupEnvelope(tasks = emptyList(), notificationSettings = imported), "2.1.0", 0),
            ImportMode.MERGE,
        )
        assertEquals(15, db.notificationSettingsDao().get()?.defaultReminder)

        applier.apply(
            BackupImport(BackupEnvelope(tasks = emptyList(), notificationSettings = imported), "2.1.0", 0),
            ImportMode.REPLACE,
        )
        assertEquals(45, db.notificationSettingsDao().get()?.defaultReminder)
    }

    @Test
    fun importedTombstoneStoresEnqueueNothing() = runTest {
        taskStore.add(draft("prior-00001", "keep"))
        val queuedBefore = db.syncQueueDao().pending().size

        val envelope = BackupEnvelope(
            tasks = emptyList(),
            archivedTasks = listOf(ArchivedTask(draft("tomb-arch01", "a"), fixed.instant())),
            deletedTasks = listOf(DeletedTask(draft("tomb-del001", "d"), fixed.instant())),
            smartViews = listOf(
                SmartView(
                    id = "custom-view1", name = "Custom", icon = "star",
                    criteria = FilterCriteria(), isBuiltIn = false,
                    createdAt = fixed.instant(), updatedAt = fixed.instant(),
                ),
            ),
            notificationSettings = BackupNotificationSettings(
                enabled = true, defaultReminder = 20, soundEnabled = true,
                quietHoursStart = null, quietHoursEnd = null, permissionAsked = false,
                updatedAt = fixed.instant(),
            ),
            archiveSettings = BackupArchiveSettings(enabled = true, archiveAfterDays = 60),
            appPreferences = BackupAppPreferences(pinnedSmartViewIds = emptyList()),
        )

        applier.apply(BackupImport(envelope, "2.1.0", 0), ImportMode.MERGE)

        assertEquals(queuedBefore, db.syncQueueDao().pending().size)
    }

    @Test
    fun anIdNeverLandsInTwoStores() = runTest {
        val envelope = BackupEnvelope(
            tasks = listOf(draft("shared-id01", "active")),
            archivedTasks = listOf(ArchivedTask(draft("shared-id01", "archived"), fixed.instant())),
        )
        applier.apply(BackupImport(envelope, "2.1.0", 0), ImportMode.MERGE)

        assertNotNull(taskStore.get("shared-id01"))
        assertNull(db.archivedTaskDao().byId("shared-id01"))
    }

    @Test
    fun importSweepsLocalTombstonesForIdsThatBecomeActive_mergeMode() = runTest {
        // Seed a local trash row and a local archive row.
        val trashed = taskStore.add(draft("trash-id001", "local-trashed"))
        taskStore.delete(trashed.id)
        val archived = taskStore.add(draft("arch-id0001", "local-archived"))
        taskStore.setCompleted(archived.id, true)
        archiveStore.archive(archived.id)
        assertNotNull(db.deletedTaskDao().byId("trash-id001"))
        assertNotNull(db.archivedTaskDao().byId("arch-id0001"))

        val envelope = BackupEnvelope(
            tasks = listOf(draft("trash-id001", "now-active-1"), draft("arch-id0001", "now-active-2")),
        )
        applier.apply(BackupImport(envelope, "2.1.0", 0), ImportMode.MERGE)

        assertNotNull(taskStore.get("trash-id001"))
        assertNotNull(taskStore.get("arch-id0001"))
        assertNull(db.deletedTaskDao().byId("trash-id001"))
        assertNull(db.archivedTaskDao().byId("arch-id0001"))
    }

    @Test
    fun importSweepsLocalTombstonesForIdsThatBecomeActive_replaceModeV1Envelope() = runTest {
        // Seed a local trash row and a local archive row (different ids from the merge-mode test).
        val trashed = taskStore.add(draft("trash-id002", "local-trashed"))
        taskStore.delete(trashed.id)
        val archived = taskStore.add(draft("arch-id0002", "local-archived"))
        taskStore.setCompleted(archived.id, true)
        archiveStore.archive(archived.id)
        assertNotNull(db.deletedTaskDao().byId("trash-id002"))
        assertNotNull(db.archivedTaskDao().byId("arch-id0002"))

        // A v1-style envelope: only a tasks array, no tombstone keys at all.
        val envelope = BackupEnvelope(
            tasks = listOf(draft("trash-id002", "now-active-1"), draft("arch-id0002", "now-active-2")),
        )
        applier.apply(BackupImport(envelope, "1", 0), ImportMode.REPLACE)

        assertNotNull(taskStore.get("trash-id002"))
        assertNotNull(taskStore.get("arch-id0002"))
        assertNull(db.deletedTaskDao().byId("trash-id002"))
        assertNull(db.archivedTaskDao().byId("arch-id0002"))
    }

    @Test
    fun importAdmitsOneHundredNewViewsAndSkipsTheRest_overwriteAtCapIsFree() = runTest {
        val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
        fun view(id: String, name: String = id) =
            SmartView(id, name, "bolt", FilterCriteria(), false, t0, t0)
        // seed one existing custom view so an overwrite can be observed
        db.smartViewDao().upsert(view("existing", "Old name").toEntity())
        val incoming = (1..100).map { view("new$it") } + view("existing", "New name") + view("new101")
        val summary = applier.apply(BackupImport(BackupEnvelope(tasks = emptyList(), smartViews = incoming), "2.1.0", 0), ImportMode.MERGE)
        val stored = db.smartViewDao().observeAll().first().filter { !it.isBuiltIn }
        assertEquals(101, stored.size)                          // existing + 100 new
        assertEquals("New name", stored.first { it.id == "existing" }.name)
        assertNull(stored.firstOrNull { it.id == "new101" })   // the 101st new id is skipped
        assertEquals(1, summary.skipped)
    }

    /** Review finding 39: one task over a local limit aborted the merge part way through. */
    @Test
    fun mergeMode_skipsATaskOverTheLimitAndImportsTheRest() = runTest {
        val envelope = BackupEnvelope(
            tasks = listOf(draft("fits-a00001", "fine"), draft("too-long001", "x".repeat(81)), draft("fits-b00001", "also fine")),
        )

        val summary = applier.apply(BackupImport(envelope, "2.1.0", 0), ImportMode.MERGE)

        assertEquals(ImportSummary(imported = 2, skipped = 1), summary)
        assertEquals(setOf("fine", "also fine"), taskStore.observeTasks().first().map { it.title }.toSet())
    }

    @Test
    fun replaceMode_skipsATaskOverTheLimitAndImportsTheRest() = runTest {
        taskStore.add(draft("old-task001", "old"))
        val envelope = BackupEnvelope(tasks = listOf(draft("fits-a00001", "fine"), draft("too-long001", "x".repeat(81))))

        val summary = applier.apply(BackupImport(envelope, "2.1.0", 0), ImportMode.REPLACE)

        assertEquals(ImportSummary(imported = 1, skipped = 1), summary)
        assertEquals(listOf("fine"), taskStore.observeTasks().first().map { it.title })
    }

    /**
     * Spec name: `a backup with no offset imports with the default`. Merge keeps this device's settings, so it
     * resolves with this device's default. Replace adopts the backup's settings in the same transaction, so a
     * task resolves with the default this device has once the import lands.
     */
    @Test
    fun aBackupWithNoOffsetImportsWithTheDefault() = runTest {
        db.notificationSettingsDao().upsert(
            NotificationSettingsEntity(
                enabled = true, defaultReminder = 30, soundEnabled = true,
                quietHoursStart = null, quietHoursEnd = null, permissionAsked = false, updatedAt = 1_000L,
            ),
        )
        val backupSettings = BackupNotificationSettings(
            enabled = true, defaultReminder = 60, soundEnabled = true,
            quietHoursStart = null, quietHoursEnd = null, permissionAsked = false, updatedAt = fixed.instant(),
        )
        val withSettings = BackupEnvelope(tasks = listOf(draft("no-offset01")), notificationSettings = backupSettings)

        applier.apply(BackupImport(withSettings, "2.1.0", 0), ImportMode.MERGE)
        assertEquals(30, taskStore.get("no-offset01")?.notifyBefore)

        applier.apply(BackupImport(BackupEnvelope(tasks = listOf(draft("no-offset02"))), "2.1.0", 0), ImportMode.REPLACE)
        assertEquals(30, taskStore.get("no-offset02")?.notifyBefore)

        applier.apply(BackupImport(withSettings, "2.1.0", 0), ImportMode.REPLACE)
        assertEquals(60, taskStore.get("no-offset01")?.notifyBefore)
    }
}
