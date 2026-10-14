package dev.vinny.gsd.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.vinny.gsd.GsdApp
import java.util.concurrent.TimeUnit

/**
 * The one periodic maintenance job (spec §11.5). Reminders never depend on it; they are
 * pre-scheduled alarms.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GsdApp).container
        runMaintenance(
            // The saved session, not the coordinator's flag: when WorkManager starts the process for
            // this job, the coordinator may not have signed in yet, and a false "signed out" would
            // sweep a stale local copy (parity backlog B-46).
            signedIn = container.authManager.currentUserId() != null,
            purgeTrash = { container.trashStore.purgeExpired() },
            sweepArchive = { container.archiveStore.sweepIfEnabled() },
            requestSync = { container.syncCoordinator.requestSync(false) },
        )
        return Result.success()
    }

    companion object {
        fun register(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "gsd-maintenance",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

/**
 * Trash retention purge first: unconditional and not user-configurable (parity spec P§3). Then, signed
 * out, the auto-archive sweep. Signed in, an opportunistic sync instead (spec §11.5), and the sweep
 * runs after the next clean sync (SyncCoordinator.afterCleanSync). Archiving deletes the
 * server copy, so a sweep from a stale local copy could delete a task another device reopened (parity
 * backlog B-46).
 */
internal suspend fun runMaintenance(
    signedIn: Boolean,
    purgeTrash: suspend () -> Unit,
    sweepArchive: suspend () -> Unit,
    requestSync: () -> Unit,
) {
    purgeTrash()
    if (signedIn) requestSync() else sweepArchive()
}
