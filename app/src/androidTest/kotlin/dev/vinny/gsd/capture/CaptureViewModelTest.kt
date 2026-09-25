package dev.vinny.gsd.capture

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vinny.gsd.model.TaskValidationError
import dev.vinny.gsd.store.GsdDatabase
import dev.vinny.gsd.store.TaskStore
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A capture the store rejects used to do nothing at all: MatrixScreen passed `onError = {}`. */
@RunWith(AndroidJUnit4::class)
class CaptureViewModelTest {
    private lateinit var db: GsdDatabase
    private lateinit var store: TaskStore
    private lateinit var viewModel: CaptureViewModel
    private val tooLong = "x".repeat(81)

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = TaskStore(db, Clock.systemUTC())
        viewModel = CaptureViewModel(store)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aRejectedCaptureKeepsItsTextAndSaysWhy(): Unit = runBlocking {
        viewModel.onTextChange(tooLong)

        viewModel.submit()

        val state = withTimeout(5_000) { viewModel.state.first { it.error != null } }
        assertIs<TaskValidationError.TitleLength>(state.error)
        assertEquals(tooLong, state.text)
        assertTrue(store.observeTasks().first().isEmpty())
    }

    @Test
    fun editingTheTextClearsTheError(): Unit = runBlocking {
        viewModel.onTextChange(tooLong)
        viewModel.submit()
        withTimeout(5_000) { viewModel.state.first { it.error != null } }

        viewModel.onTextChange(tooLong.dropLast(1))

        assertNull(viewModel.state.value.error)
    }
}
