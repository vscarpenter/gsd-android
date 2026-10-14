package dev.vinny.gsd.notifications

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Parity backlog B-46: archiving deletes each task's server copy, so a signed-in sweep must not run
 * from the local copy before a sync has pulled. Signed in, the job syncs and the sweep follows that
 * sync's pull (SyncCoordinator.afterCleanSync). Signed out, nothing leaves the device, so it sweeps.
 */
class MaintenanceTest {
    private val calls = mutableListOf<String>()

    private suspend fun maintain(signedIn: Boolean) = runMaintenance(
        signedIn = signedIn,
        purgeTrash = { calls += "purge" },
        sweepArchive = { calls += "sweep" },
        requestSync = { calls += "sync" },
    )

    @Test
    fun `signed out, it purges trash and then sweeps the archive`() = runTest {
        maintain(signedIn = false)

        assertEquals(listOf("purge", "sweep"), calls)
    }

    @Test
    fun `signed in, it purges trash and syncs, leaving the sweep for after the pull`() = runTest {
        maintain(signedIn = true)

        assertEquals(listOf("purge", "sync"), calls)
    }
}
