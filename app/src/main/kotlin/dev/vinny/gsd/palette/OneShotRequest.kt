package dev.vinny.gsd.palette

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A request that waits for its consumer and is delivered exactly once (spec §6.4). A plain
 * StateFlow replays its value to every new collector, so a consumed "new task" request opened
 * the editor again on every tab switch and rotation. Here the consumer claims the value first:
 * a request sent before the screen composes still arrives, and nothing is ever handled twice.
 */
class OneShotRequest<T : Any> {
    private val pending = MutableStateFlow<T?>(null)

    fun send(value: T) {
        pending.value = value
    }

    suspend fun consume(handle: suspend (T) -> Unit): Nothing =
        pending.collect { value ->
            if (value != null && pending.compareAndSet(value, null)) handle(value)
        }
}
