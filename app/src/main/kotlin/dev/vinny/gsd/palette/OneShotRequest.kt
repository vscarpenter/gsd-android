package dev.vinny.gsd.palette

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A request that waits for its consumer and is delivered exactly once (spec §6.4). A plain
 * StateFlow replays its value to every new collector, so a consumed "new task" request opened
 * the editor again on every tab switch and rotation. Here the consumer claims the value first:
 * a request sent before the screen composes still arrives, and nothing is ever handled twice.
 */
class OneShotRequest<T : Any> {
    /**
     * Each send is its own object, compared by identity. A StateFlow skips a value equal to the last
     * one its collector saw, and the real requests carry Unit. Without this, a request sent while a
     * handler was suspended was never delivered, because Unit == Unit.
     */
    private class Sent<T>(val value: T)

    private val pending = MutableStateFlow<Sent<T>?>(null)

    fun send(value: T) {
        pending.value = Sent(value)
    }

    suspend fun consume(handle: suspend (T) -> Unit): Nothing =
        pending.collect { sent ->
            if (sent != null && pending.compareAndSet(sent, null)) handle(sent.value)
        }
}
