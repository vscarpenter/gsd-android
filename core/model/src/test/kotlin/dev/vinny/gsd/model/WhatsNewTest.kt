package dev.vinny.gsd.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The bundled notes ship inside every client, so a malformed entry would break What's New on
 * three platforms at once. Mirrors the web repo's content contract (tests/data/whats-new-content.test.ts).
 */
class WhatsNewTest {
    private val notes = WhatsNew.bundled()

    @Test
    fun `bundled notes are schema 1 with every platform`() {
        assertEquals(1, notes.schema)
        for (platform in WhatsNewPlatform.entries) {
            assertTrue(notes.releases.any { it.platform == platform }, "missing $platform")
        }
    }

    @Test
    fun `every release meets the content contract`() {
        val problems = buildList {
            for (r in notes.releases) {
                val label = "${r.platform} ${r.version}"
                if (WhatsNew.parseVersion(r.version) == null) add("$label: version")
                if (!Regex("""\d{4}-\d{2}-\d{2}""").matches(r.date)) add("$label: date")
                if (r.notable && r.headline.isNullOrBlank()) add("$label: notable without headline")
                if (!r.notable && r.headline != null) add("$label: headline on a non-notable release")
                val items = r.new + r.improved + r.fixed
                if (items.isEmpty()) add("$label: no items")
                if ((items + listOfNotNull(r.headline)).any { it.isBlank() || '—' in it }) add("$label: blank item or em dash")
            }
        }
        assertEquals(emptyList(), problems)
    }

    @Test
    fun `no platform and version repeats`() {
        val keys = notes.releases.map { "${it.platform}@${it.version}" }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `parses valid versions`() {
        assertEquals(WhatsNewVersion(14, 6, 2), WhatsNew.parseVersion("14.6.2"))
        assertEquals(WhatsNewVersion(0, 10, 0), WhatsNew.parseVersion("0.10.0"))
    }

    @Test
    fun `rejects invalid versions`() {
        for (input in listOf("14.6", "14.6.2.1", "v14.6.2", "14.06.2", "14.6.2-beta", "", " 14.6.2", "1..2", "+1.2.3")) {
            assertNull(WhatsNew.parseVersion(input), "accepted \"$input\"")
        }
    }

    @Test
    fun `compares numerically, not as text`() {
        val older = assertNotNull(WhatsNew.parseVersion("1.2.0"))
        val newer = assertNotNull(WhatsNew.parseVersion("1.10.0"))
        assertTrue(older < newer)
    }

    private fun release(platform: WhatsNewPlatform, version: String) =
        WhatsNewRelease(platform, version, "2026-01-01", notable = true, headline = "H", new = listOf("x"))

    @Test
    fun `keeps one platform, newest first, up to the running version`() {
        val all = listOf(
            release(WhatsNewPlatform.ANDROID, "1.2.0"),
            release(WhatsNewPlatform.ANDROID, "1.10.0"),
            release(WhatsNewPlatform.WEB, "9.0.0"),
            release(WhatsNewPlatform.ANDROID, "1.0.0"),
            release(WhatsNewPlatform.ANDROID, "2.0.0"),
        )
        assertEquals(
            listOf("1.10.0", "1.2.0", "1.0.0"),
            WhatsNew.releasesFor(all, WhatsNewPlatform.ANDROID, "1.10.0").map { it.version },
        )
    }

    @Test
    fun `keeps every release when the running version is unknown`() {
        val all = listOf(release(WhatsNewPlatform.ANDROID, "1.0.0"), release(WhatsNewPlatform.ANDROID, "2.0.0"))
        assertEquals(2, WhatsNew.releasesFor(all, WhatsNewPlatform.ANDROID, null).size)
        assertEquals(2, WhatsNew.releasesFor(all, WhatsNewPlatform.ANDROID, "dev").size)
    }
}
