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
 * The one periodic maintenance job (spec §11.5): auto-archive sweep when
 * enabled. Opportunistic sync joins in Phase 5. Reminders never depend on
 * this — they are pre-scheduled alarms.
 */
class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GsdApp).container
        val settings = container.db.archiveSettingsDao().get()
        if (settings?.enabled == true) {
            container.archiveStore.sweep(settings.archiveAfterDays)
        }
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
