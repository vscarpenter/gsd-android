package dev.vinny.gsd.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The totals on the sync history screen (backlog B-28), defined as on web and iOS. */
@RunWith(AndroidJUnit4::class)
class SyncHistoryTotalsTest {
    private lateinit var db: GsdDatabase

    @Before
    fun setUp() {
        db = GsdDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun attempt(status: String, pushed: Int, pulled: Int) {
        db.syncHistoryDao().insert(
            SyncHistoryEntity(
                timestamp = 1_790_000_000_000, status = status, pushedCount = pushed, pulledCount = pulled,
                conflictsResolved = 0, failedCount = null, errorMessage = null, duration = 10,
                deviceId = "device-1", triggeredBy = "auto",
            ),
        )
    }

    @Test
    fun anEmptyHistoryTotalsZero() = runTest {
        assertEquals(SyncHistoryTotals(), db.syncHistoryDao().observeTotals().first())
    }

    @Test
    fun totalsCountEveryAttemptAndOnlySuccessesAsSuccessful() = runTest {
        attempt("success", pushed = 3, pulled = 2)
        attempt("partial", pushed = 1, pulled = 4)
        attempt("error", pushed = 0, pulled = 0)
        attempt("success", pushed = 5, pulled = 0)

        val totals = db.syncHistoryDao().observeTotals().first()

        assertEquals(SyncHistoryTotals(totalSyncs = 4, successes = 2, totalPushed = 9, totalPulled = 6), totals)
    }
}
