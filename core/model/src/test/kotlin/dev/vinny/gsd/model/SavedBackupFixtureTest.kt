package dev.vinny.gsd.model

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/** The web's Saved export (byte-identical copy of the iOS fixture, md5 8d46a3b1…; backlog B-50). */
class SavedBackupFixtureTest {
    private fun fixture(name: String): String = checkNotNull(
        javaClass.getResourceAsStream("/fixtures/cross-platform/$name"),
    ).readBytes().decodeToString()

    @Test
    fun `the web Saved export imports with placement and notes intact`() {
        val parsed = Interchange.parse(fixture("web-saved-14.2.0.json"))
        assertEquals(0, parsed.skipped)
        val saved = parsed.envelope.tasks.single()
        assertEquals("fkXzSdXIafQr", saved.id)
        assertEquals(Instant.parse("2026-10-26T12:00:00.000Z"), saved.savedAt)
        assertEquals(true, saved.description.startsWith("Read when this project becomes active."))
    }

    @Test
    fun `an Android re-export keeps Saved placement`() {
        val first = Interchange.parse(fixture("web-saved-14.2.0.json"))
        val second = Interchange.parse(Interchange.export(first.envelope, Instant.parse("2026-10-29T12:00:00.000Z")))
        assertEquals(first.envelope.tasks, second.envelope.tasks)
    }
}
