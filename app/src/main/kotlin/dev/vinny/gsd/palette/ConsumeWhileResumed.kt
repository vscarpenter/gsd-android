package dev.vinny.gsd.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Takes requests only while this screen is RESUMED. A composition outlives onStop, so a plain
 * effect on a covered activity could claim a request meant for the one on top (spec §6.4). STARTED
 * is not enough either: a shortcut's CLEAR_TASK pauses the running activity and then creates the
 * fresh one, and the paused copy is still STARTED when the request lands (review finding 46).
 */
@Composable
fun <T : Any> ConsumeWhileResumed(requests: OneShotRequest<T>, handle: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentHandle by rememberUpdatedState(handle)
    LaunchedEffect(requests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            requests.consume { currentHandle(it) }
        }
    }
}
