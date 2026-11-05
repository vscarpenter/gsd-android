package dev.vinny.gsd.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What's New (gsd-taskmanager tasks/spec-whats-new.md, decision D-15).
 *
 * `whats-new.json` (this module's resources) is a byte-identical copy of the web repo's
 * `lib/whats-new/whats-new.json`, which holds every client's release notes. It ships inside the
 * build, so What's New works offline and never asks a server what changed. [WhatsNew.decide] is
 * pinned by the shared `whats-new-corpus.json`, which the web and iOS suites run too. Change the
 * web authority first, then copy both files here.
 */
@Serializable
enum class WhatsNewPlatform {
    @SerialName("web") WEB,
    @SerialName("apple") APPLE,
    @SerialName("android") ANDROID,
}

@Serializable
data class WhatsNewRelease(
    val platform: WhatsNewPlatform,
    val version: String,
    /** Calendar date, YYYY-MM-DD. */
    val date: String,
    /** True when this release may show the one-line post-update cue. */
    val notable: Boolean,
    /** Required when notable; null otherwise. */
    val headline: String? = null,
    val new: List<String> = emptyList(),
    val improved: List<String> = emptyList(),
    val fixed: List<String> = emptyList(),
)

@Serializable
data class WhatsNewNotes(val schema: Int, val releases: List<WhatsNewRelease>)

data class WhatsNewVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<WhatsNewVersion> {
    override fun compareTo(other: WhatsNewVersion): Int =
        compareValuesBy(this, other, WhatsNewVersion::major, WhatsNewVersion::minor, WhatsNewVersion::patch)
}

object WhatsNew {
    /** What the rule needs from a release: its version and whether it is notable. */
    data class Entry(val version: String, val notable: Boolean)

    data class Decision(
        val show: Boolean,
        val headlineVersion: String?,
        val unseenCount: Int,
        /** Written as last seen right away, with no user action. Null writes nothing. */
        val recordVersion: String?,
    )

    private val NOTHING = Decision(show = false, headlineVersion = null, unseenCount = 0, recordVersion = null)
    private const val VERSION_PARTS = 3
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Three dot-separated non-negative integers with no leading zeros (0 itself is fine) and
     * nothing else, so every client parses exactly the same set of strings.
     */
    fun parseVersion(value: String): WhatsNewVersion? {
        val parts = value.split('.')
        if (parts.size != VERSION_PARTS) return null
        val numbers = parts.map { part ->
            val valid = part.isNotEmpty() && part.all { it in '0'..'9' } && (part == "0" || part[0] != '0')
            if (!valid) return null
            part.toIntOrNull() ?: return null
        }
        return WhatsNewVersion(numbers[0], numbers[1], numbers[2])
    }

    /**
     * One platform's releases, newest first. Notes for a version newer than the running one are
     * left out, so an entry can land before the build that ships it. An unknown running version
     * keeps them all.
     */
    fun releasesFor(all: List<WhatsNewRelease>, platform: WhatsNewPlatform, currentVersion: String?): List<WhatsNewRelease> {
        val current = currentVersion?.let(::parseVersion)
        return all
            .mapNotNull { release ->
                val version = parseVersion(release.version)
                if (release.platform != platform || version == null) return@mapNotNull null
                if (current != null && version > current) return@mapNotNull null
                release to version
            }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    /** Whether the post-update line shows. The spec's section 4 explains each branch; the corpus pins them. */
    fun decide(releases: List<Entry>, currentVersion: String, lastSeenVersion: String?, existingUser: Boolean): Decision {
        val current = parseVersion(currentVersion) ?: return NOTHING
        val lastSeen = lastSeenVersion?.let(::parseVersion)
        val startFromHere = NOTHING.copy(recordVersion = currentVersion)

        if (lastSeen == null && !existingUser) return startFromHere
        if (lastSeen != null && lastSeen >= current) return NOTHING

        val counted = releases
            .mapNotNull { entry -> parseVersion(entry.version)?.takeIf { it <= current }?.let { Triple(entry.version, it, entry.notable) } }
            .sortedByDescending { it.second }

        val unseen = if (lastSeen != null) {
            counted.filter { it.second > lastSeen }
        } else {
            val newestNotable = counted.firstOrNull { it.third }
            if (newestNotable == null) emptyList() else counted.filter { it.second >= newestNotable.second }
        }

        val headline = unseen.firstOrNull { it.third } ?: return startFromHere
        return Decision(show = true, headlineVersion = headline.first, unseenCount = unseen.size, recordVersion = null)
    }

    /** The notes bundled with this build. */
    fun bundled(): WhatsNewNotes {
        val raw = checkNotNull(WhatsNew::class.java.getResourceAsStream("/whats-new.json")) {
            "whats-new.json is missing from :core:model resources"
        }.use { it.readBytes().decodeToString() }
        return json.decodeFromString(WhatsNewNotes.serializer(), raw)
    }
}
