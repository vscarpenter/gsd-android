package dev.vinny.gsd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeAccount : AccountRemote {
    var routeStatus: Int? = 204          // null = transport failure
    var userDeleted = false
    var rejectUserDelete = false
    val routeCalls = mutableListOf<Unit>()
    override suspend fun deleteAccountViaRoute() {
        routeCalls += Unit
        when (val s = routeStatus) {
            204 -> Unit
            null -> throw java.io.IOException("offline")
            else -> throw PocketBaseApi.HttpError(s, "")
        }
    }
    override suspend fun deleteUser(userId: String) {
        if (rejectUserDelete) throw PocketBaseApi.Unauthorized(401)
        userDeleted = true
    }
}

class AccountDeleterTest {
    @Test
    fun `route 204 deletes without enumerating`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount()
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.Deleted, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size)     // fallback never ran
        assertTrue(!account.userDeleted)         // the route owns the user delete
    }

    @Test
    fun `route 404 falls back, deletes every task, then the user`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        tasks.records["r1"] = WireTask(taskId = "a"); tasks.records["r2"] = WireTask(taskId = "b")
        assertEquals(DeleteAccountOutcome.Deleted, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(tasks.records.isEmpty()); assertTrue(account.userDeleted)
    }

    @Test
    fun `a transport failure on the route stops with nothing changed`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = null }
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.ListingFailed, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size); assertTrue(!account.userDeleted)
    }

    @Test
    fun `a listing failure before any delete reports ListingFailed and touches nothing`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        tasks.records["r1"] = WireTask(taskId = "a")
        tasks.listResponder = { page, _ -> TaskListPage(page, 1, 10_001, emptyList(), emptyList()) }
        assertEquals(DeleteAccountOutcome.ListingFailed, AccountDeleter(tasks, account, 0).delete("u1"))
        assertEquals(1, tasks.records.size); assertTrue(!account.userDeleted)
    }

    @Test
    fun `an auth rejection after some deletes reports PartialAuthRejected`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404; rejectUserDelete = true }
        tasks.records["r1"] = WireTask(taskId = "a")
        assertEquals(DeleteAccountOutcome.PartialAuthRejected, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(tasks.records.isEmpty()); assertTrue(!account.userDeleted)
    }

    @Test
    fun `the user record is never deleted while a listing still returns rows`() = runTest {
        val tasks = FakeRemote(); val account = FakeAccount().apply { routeStatus = 404 }
        // a remote that always reports one row the client cannot delete
        tasks.listResponder = { page, _ -> TaskListPage(page, 1, 1, listOf(WireTask(id = "ghost", taskId = "g")), listOf("")) }
        assertEquals(DeleteAccountOutcome.PartialRetry, AccountDeleter(tasks, account, 0).delete("u1"))
        assertTrue(!account.userDeleted)
    }
}
