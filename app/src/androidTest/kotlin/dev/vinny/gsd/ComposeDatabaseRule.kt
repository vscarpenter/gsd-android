package dev.vinny.gsd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import dev.vinny.gsd.store.GsdDatabase
import kotlinx.coroutines.cancel
import org.junit.rules.ExternalResource

/**
 * An in-memory database for a Compose test, closed only once nothing the test built can still query it
 * (AND-TEARDOWN-RACE). Closing it in `@After` raced in two ways. JUnit runs `@After` inside the Compose rule, so the
 * content was still composed. And a view model built by hand outlives the content: `stateIn(WhileSubscribed(5_000))`
 * keeps its Room flow alive for five seconds after the last collector leaves. A write near the end of a test then
 * re-queried a closed pool.
 *
 * Chain it outside the Compose rule, and pass it every view model the test builds by hand. Once the Compose rule has
 * disposed the content, it cancels those view models' scopes, then closes the database.
 */
class ComposeDatabaseRule : ExternalResource() {
    lateinit var db: GsdDatabase
        private set
    private val viewModels = mutableListOf<ViewModel>()

    /** Ends [viewModel]'s scope before the database closes. Returns it, for use in an assignment. */
    fun <T : ViewModel> track(viewModel: T): T = viewModel.also { viewModels += it }

    override fun before() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    override fun after() {
        viewModels.forEach { it.viewModelScope.cancel() }
        db.close()
    }
}
