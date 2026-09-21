package dev.vinny.gsd.editor

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.Task
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

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

    /**
     * Review finding 44: the editor saved the whole row it read when it opened. A Complete tapped on
     * the reminder in the meantime came back undone, with a fresh updatedAt that wins elsewhere too.
     */
    @Test
    fun aSaveKeepsACompletionMadeWhileTheEditorWasOpen(): Unit = runBlocking {
        val saved = store.add(Task(title = "existing", urgent = true, important = true, createdAt = t0, updatedAt = t0))
        viewModel.openExisting(saved.id)
        viewModel.draft.first { it != null }

        store.setCompleted(saved.id, true) // the Complete button on the reminder
        viewModel.edit { it.copy(title = "retitled") }
        val done = CompletableDeferred<Unit>()
        viewModel.save(onDone = { done.complete(Unit) }, onError = { done.completeExceptionally(AssertionError(it)) })
        withTimeout(5_000) { done.await() }

        val stored = assertNotNull(store.get(saved.id))
        assertEquals("retitled", stored.title)
        assertTrue(stored.completed, "the save undid a completion it never saw")
        assertNotNull(stored.completedAt)
    }

    /** The old save upserted the snapshot, so a task deleted while the sheet was open came back. */
    @Test
    fun aSaveOnATaskDeletedWhileTheEditorWasOpenWritesNothing(): Unit = runBlocking {
        val saved = store.add(Task(title = "existing", urgent = true, important = true, createdAt = t0, updatedAt = t0))
        viewModel.openExisting(saved.id)
        viewModel.draft.first { it != null }

        store.delete(saved.id)
        viewModel.edit { it.copy(title = "too late") }
        val outcome = CompletableDeferred<String>()
        viewModel.save(
            onDone = { outcome.complete("done") },
            onError = { outcome.complete("error: $it") },
            onMissing = { outcome.complete("missing") },
        )

        assertEquals("missing", withTimeout(5_000) { outcome.await() })
        assertEquals(null, store.get(saved.id))
        assertEquals(null, viewModel.draft.value) // the sheet closes
    }

    @Test
    fun aSaveStillRejectsATitleThatIsTooLong(): Unit = runBlocking {
        val saved = store.add(Task(title = "existing", urgent = true, important = true, createdAt = t0, updatedAt = t0))
        viewModel.openExisting(saved.id)
        viewModel.draft.first { it != null }

        viewModel.edit { it.copy(title = "x".repeat(81)) }
        val outcome = CompletableDeferred<String>()
        viewModel.save(onDone = { outcome.complete("done") }, onError = { outcome.complete("error") })

        assertEquals("error", withTimeout(5_000) { outcome.await() })
        assertEquals("existing", store.get(saved.id)?.title)
        assertNotNull(viewModel.draft.value) // the sheet stays open so the user can shorten it
    }
}
