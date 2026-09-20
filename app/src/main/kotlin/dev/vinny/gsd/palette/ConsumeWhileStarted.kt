package dev.vinny.gsd.palette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/**
 * Takes requests only while this screen is at least STARTED. A composition outlives onStop, so a
 * plain effect on a covered activity could claim a request meant for the one on top (spec §6.4).
 */
@Composable
fun <T : Any> ConsumeWhileStarted(requests: OneShotRequest<T>, handle: suspend (T) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentHandle by rememberUpdatedState(handle)
    LaunchedEffect(requests, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            requests.consume { currentHandle(it) }
        }
    }
}
