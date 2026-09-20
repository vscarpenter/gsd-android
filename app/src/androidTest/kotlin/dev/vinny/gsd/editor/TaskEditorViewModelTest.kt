package dev.vinny.gsd.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Review finding S4: a New Task request that arrives while the editor is open must not wipe the draft. */
@RunWith(AndroidJUnit4::class)
class TaskEditorViewModelTest {
    private val t0 = Instant.parse("2026-07-19T12:00:00.000Z")
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var viewModel: TaskEditorViewModel

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, Clock.systemUTC())
        viewModel = TaskEditorViewModel(store)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aNewTaskRequestKeepsAHalfTypedDraft() {
        viewModel.openNew(null)
        viewModel.edit { it.copy(title = "half-typed") }

        viewModel.openNew(null) // Ctrl+N again, or a widget tap, with the sheet still open

        assertEquals("half-typed", viewModel.draft.value?.title)
    }

    @Test
    fun aNewTaskRequestKeepsAnOpenEdit() = runTest {
        val saved = store.add(Task(title = "existing", urgent = true, important = true, createdAt = t0, updatedAt = t0))
        viewModel.openExisting(saved.id)
        viewModel.draft.first { it != null }

        viewModel.openNew(null)

        assertEquals(saved.id, viewModel.draft.value?.id)
        assertFalse(viewModel.isNew) // a save must still update the task, not add a copy
    }
}
