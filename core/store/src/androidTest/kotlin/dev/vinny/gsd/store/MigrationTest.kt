package dev.vinny.gsd.store

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), GsdDatabase::class.java)

    @Test
    fun migrate1To2_createsDeletedTasks_andPreservesRows() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL(
                """INSERT INTO tasks (id, title, description, urgent, important, quadrant,
                completed, createdAt, updatedAt, recurrence, tagsJson, subtasksJson,
                dependenciesJson, notificationEnabled, notificationSent, timeEntriesJson)
                VALUES ('t1', 'survives', '', 0, 0, 'not-urgent-not-important',
                0, 1, 1, 'none', '[]', '[]', '[]', 1, 0, '[]')""",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 2, true, GsdDatabase.MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM tasks").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT COUNT(*) FROM deleted_tasks").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun migrate2To3_addsSavedAt_andAContractVersionThatForcesOneBackfill() {
        helper.createDatabase("migration-test-3", 2).apply {
            val columns = """id, title, description, urgent, important, quadrant, completed, createdAt,
                updatedAt, recurrence, tagsJson, subtasksJson, dependenciesJson, notificationEnabled,
                notificationSent, timeEntriesJson"""
            val values = "'%s', 'survives', '', 0, 1, 'not-urgent-important', 0, 1, 1, 'none', '[]', '[]', '[]', 1, 0, '[]'"
            execSQL("INSERT INTO tasks ($columns) VALUES (${values.format("t1")})")
            execSQL("INSERT INTO archived_tasks ($columns, archivedAt) VALUES (${values.format("a1")}, 2)")
            execSQL("INSERT INTO deleted_tasks ($columns, deletedAt) VALUES (${values.format("d1")}, 3)")
            execSQL("INSERT INTO sync_metadata (id, cursor, deviceId, deviceName) VALUES (1, '2026-10-27 09:00:00.000Z', 'dev', 'Pixel')")
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test-3", 3, true, GsdDatabase.MIGRATION_2_3)
        for (table in listOf("tasks", "archived_tasks", "deleted_tasks")) {
            db.query("SELECT COUNT(*), MAX(savedAt) FROM $table").use {
                it.moveToFirst()
                assertEquals(1, it.getInt(0))
                assertEquals(true, it.isNull(1))
            }
        }
        db.query("SELECT cursor, contractVersion FROM sync_metadata").use {
            it.moveToFirst()
            assertEquals("2026-10-27 09:00:00.000Z", it.getString(0))
            assertEquals(0, it.getInt(1))
        }
    }
}
