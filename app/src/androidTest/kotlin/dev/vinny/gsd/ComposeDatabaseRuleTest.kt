package dev.vinny.gsd

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.junit.rules.RuleChain
import org.junit.rules.Verifier
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * AND-TEARDOWN-RACE. Closing the database in `@After` closed it while the content was still composed, so anything
 * the content queried as it left hit a closed pool. With that pattern, this test failed every run: Room's own scope
 * was already cancelled when the content's `onDispose` queried it.
 */
class ComposeDatabaseRuleTest {
    private val database = ComposeDatabaseRule()
    private val rule = createComposeRule()
    private var queryAtDispose: Result<Int>? = null

    /** Outermost, so it runs after the Compose rule has disposed the content: what the content saw as it left. */
    private val verifier = object : Verifier() {
        override fun verify() {
            val result = checkNotNull(queryAtDispose) { "the content was never disposed" }
            assertTrue(result.isSuccess, "the database closed before the content left: ${result.exceptionOrNull()}")
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(verifier).around(database).around(rule)

    @Test
    fun theContentCanStillQueryTheDatabaseAsItLeaves() {
        rule.setContent {
            DisposableEffect(Unit) {
                onDispose { queryAtDispose = runCatching { runBlocking { database.db.taskDao().all().size } } }
            }
        }
        rule.waitForIdle()
    }
}

/** The other half: a view model the test built by hand would keep querying, so its scope ends with the test. */
class ComposeDatabaseRuleViewModelTest {
    @Test
    fun aTrackedViewModelsScopeEndsAndTheDatabaseCloses() {
        val database = ComposeDatabaseRule()
        var job: Job? = null
        val test = object : Statement() {
            override fun evaluate() {
                val viewModel = database.track(object : ViewModel() {})
                job = viewModel.viewModelScope.launch(Dispatchers.Default) { awaitCancellation() }
            }
        }

        database.apply(test, Description.EMPTY).evaluate()

        assertTrue(checkNotNull(job).isCancelled, "the view model's scope outlived the test")
        assertFalse(database.db.isOpen, "the database stayed open")
    }
}
