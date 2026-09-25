package dev.vinny.gsd.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Review finding 8 and "Also worth a look": a granted exact-alarm permission, a new time zone, and a
 * clock change each move a fire time, so the manifest must route all of them to the reschedule. An
 * alarm is invisible to a test, so this checks the routing the system itself reads.
 */
@RunWith(AndroidJUnit4::class)
class RescheduleReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun receiversFor(action: String): List<String> =
        context.packageManager
            .queryBroadcastReceivers(Intent(action).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }

    @Test
    fun everyChangeThatMovesAFireTimeReachesTheReschedule() {
        val actions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )

        val missing = actions.filter { BootReceiver::class.java.name !in receiversFor(it) }

        assertEquals(emptyList(), missing)
    }
}
