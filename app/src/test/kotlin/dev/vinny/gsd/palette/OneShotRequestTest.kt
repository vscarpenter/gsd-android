package dev.vinny.gsd.palette

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Review finding S4: a request is an event. It waits for its consumer, and it is never replayed. */
@OptIn(ExperimentalCoroutinesApi::class)
class OneShotRequestTest {

    @Test
    fun `a request sent before anyone listens is delivered when a consumer arrives`() = runTest {
        val requests = OneShotRequest<String>()
        val handled = mutableListOf<String>()
        requests.send("new-task") // a cold-start deep link lands before the screen composes

        val consumer = launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { handled += it } }

        assertEquals(listOf("new-task"), handled)
        consumer.cancel()
    }

    @Test
    fun `a consumed request is not replayed to a later consumer`() = runTest {
        val requests = OneShotRequest<String>()
        val first = mutableListOf<String>()
        val second = mutableListOf<String>()
        requests.send("new-task")
        launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { first += it } }.cancel()

        // the screen comes back: a tab switch or a rotation starts a fresh consumer
        val later = launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { second += it } }

        assertEquals(listOf("new-task"), first)
        assertEquals(emptyList(), second)
        later.cancel()
    }

    @Test
    fun `with two consumers only one handles a request`() = runTest {
        val requests = OneShotRequest<String>()
        val handled = mutableListOf<String>()
        val a = launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { handled += "a:$it" } }
        val b = launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { handled += "b:$it" } }

        requests.send("new-task")

        assertEquals(1, handled.size)
        a.cancel(); b.cancel()
    }

    @Test
    fun `each new request is delivered after the one before it`() = runTest {
        val requests = OneShotRequest<String>()
        val handled = mutableListOf<String>()
        val consumer = launch(UnconfinedTestDispatcher(testScheduler)) { requests.consume { handled += it } }

        requests.send("first")
        requests.send("second")

        assertEquals(listOf("first", "second"), handled)
        consumer.cancel()
    }
}
