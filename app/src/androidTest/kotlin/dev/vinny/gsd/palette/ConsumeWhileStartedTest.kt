package dev.vinny.gsd.palette

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Review finding S4: a deep link can stack a second MainActivity on the first. Both host a Matrix
 * screen, and only the one the user can see may take the request.
 */
class ConsumeWhileStartedTest {
    @get:Rule
    val rule = createComposeRule()

    private class Screen : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun aStoppedScreenLeavesTheRequestForAVisibleOne() {
        val requests = OneShotRequest<String>()
        val handled = mutableListOf<String>()
        val screen = Screen()
        rule.runOnUiThread { screen.registry.currentState = Lifecycle.State.CREATED } // stopped, not destroyed
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides screen) {
                ConsumeWhileStarted(requests) { handled += it }
            }
        }

        requests.send("new-task")
        rule.waitForIdle()
        assertEquals(emptyList(), handled)

        rule.runOnUiThread { screen.registry.currentState = Lifecycle.State.STARTED }
        rule.waitUntil(5_000) { handled.isNotEmpty() }
        assertEquals(listOf("new-task"), handled)
    }
}
