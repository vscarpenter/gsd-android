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
}
