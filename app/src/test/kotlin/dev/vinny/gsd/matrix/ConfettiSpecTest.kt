package dev.vinny.gsd.matrix

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfettiSpecTest {
    @Test
    fun `spec 5-8 parameters`() {
        assertEquals(160, ConfettiSpec.COUNT)
        assertEquals(1600, ConfettiSpec.DURATION_MS)
        assertEquals(700f, ConfettiSpec.GRAVITY)
    }

    @Test
    fun `particles are within launch ranges`() {
        val particles = ConfettiSpec.particles(Random(7))
        assertEquals(160, particles.size)
        particles.forEach { p ->
            assertTrue(p.xNorm in 0.3f..0.7f, "xNorm ${p.xNorm}")
            assertTrue(p.speed in 150f..460f, "speed ${p.speed}")
            assertTrue(p.sizeDp in 5f..10f, "size ${p.sizeDp}")
            assertTrue(p.colorIndex in 0..4, "colorIndex ${p.colorIndex}")
        }
    }

    @Test
    fun `seeded generation is deterministic`() {
        assertEquals(ConfettiSpec.particles(Random(1)), ConfettiSpec.particles(Random(1)))
    }
}
