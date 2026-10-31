package dev.vinny.gsd.sync

import dev.vinny.gsd.store.SyncOp
import java.time.Instant

/**
 * `client_updated_at` comes from the writing device's clock, so a skewed or forged far-future stamp must
 * not win last-write-wins forever (backlog B-44). The numbers and boundary match the web's
 * `SYNC_CONFIG.MAX_CLIENT_CLOCK_SKEW_MS`, `clampClientTimestamp`, and `isRemoteNewer`.
 */
object RemoteStamp {
    private const val MAX_CLOCK_SKEW_MS = 5 * 60 * 1000L

    private fun ceilingMs(now: Instant): Long = now.toEpochMilli() + MAX_CLOCK_SKEW_MS

    fun isPastCeiling(remote: Instant, now: Instant): Boolean = remote.toEpochMilli() > ceilingMs(now)

    /** A remote stamp as ingested: compare and store this value, or a stored 2099 beats every later edit. */
    fun capped(remote: Instant, now: Instant): Instant =
        if (isPastCeiling(remote, now)) Instant.ofEpochMilli(ceilingMs(now)) else remote

    /** Whether a remote may overrule a queued write or a tombstone. A far-future stamp is never newer. */
    fun isNewer(remote: Instant, thanMs: Long, now: Instant): Boolean =
        !isPastCeiling(remote, now) && remote.toEpochMilli() > thanMs
}

/**
 * A queued create or update, pending or failed. Android pulls before it pushes, so a capped far-future
 * stamp would overwrite an edit that's about to heal the server. A failed row isn't pushed this run, but
 * a later revive pushes it. A delete isn't a write this rule protects.
 */
internal suspend fun SyncLocalStore.hasQueuedWrite(taskId: String): Boolean =
    (pendingQueue() + failedQueue()).any { it.taskId == taskId && it.operation != SyncOp.DELETE }
