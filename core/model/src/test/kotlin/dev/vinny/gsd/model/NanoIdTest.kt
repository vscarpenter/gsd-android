package dev.vinny.gsd.model

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanoIdTest {
    @Test
    fun `default length is 21`() = assertEquals(21, NanoId.generate().length)

    @Test
    fun `explicit lengths are honored`() {
        assertEquals(8, NanoId.generate(8).length)
        assertEquals(12, NanoId.generate(12).length)
    }

    @Test
    fun `only url-safe alphabet characters`() {
        val allowed = (('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_')).toSet()
        repeat(100) { NanoId.generate().forEach { c -> assertTrue(c in allowed, "bad char $c") } }
    }

    @Test
    fun `seeded random is deterministic`() {
        assertEquals(NanoId.generate(21, Random(42)), NanoId.generate(21, Random(42)))
    }

    @Test
    fun `no collisions in a 10k sample`() {
        val ids = (1..10_000).map { NanoId.generate() }.toSet()
        assertEquals(10_000, ids.size)
    }
}
