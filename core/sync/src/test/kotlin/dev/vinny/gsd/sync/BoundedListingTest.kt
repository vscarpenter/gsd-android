package dev.vinny.gsd.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedListingTest {
    private fun wire(taskId: String, recordId: String = "r-$taskId") = WireTask(id = recordId, taskId = taskId)
    private fun page(page: Int, totalPages: Int, totalItems: Int, items: List<WireTask>) =
        TaskListPage(page = page, totalPages = totalPages, totalItems = totalItems, items = items, updatedStamps = items.map { "" })

    @Test
    fun `walks every page and returns the full listing`() = runTest {
        val pages = listOf(page(1, 2, 3, listOf(wire("a"), wire("b"))), page(2, 2, 3, listOf(wire("c"))))
        val all = BoundedListing.collect("cursor", { p, _ -> pages[p - 1] })
        assertEquals(listOf("a", "b", "c"), all.map { it.taskId })
    }

    @Test
    fun `over the cap fails closed before any page is trusted`() = runTest {
        val e = assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { _, _ -> page(1, 51, 10_001, listOf(wire("a"))) })
        }
        assertEquals("totalItems 10001 exceeds cap 10000", e.message)
    }

    @Test
    fun `a wrong page number fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { p, _ -> page(if (p == 2) 3 else 1, 2, 2, listOf(wire("x$p"))) })
        }
    }

    @Test
    fun `totalItems changing mid-walk fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { p, _ -> if (p == 1) page(1, 2, 2, listOf(wire("a"))) else page(2, 2, 3, listOf(wire("b"))) })
        }
    }

    @Test
    fun `an empty page before the last page fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { p, _ -> if (p == 1) page(1, 2, 1, emptyList()) else page(2, 2, 1, listOf(wire("a"))) })
        }
    }

    @Test
    fun `a final count that differs from totalItems fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { _, _ -> page(1, 1, 2, listOf(wire("a"))) })
        }
    }

    @Test
    fun `a duplicate task id fails closed`() = runTest {
        assertFailsWith<RemoteListingIncomplete> {
            BoundedListing.collect("c", { _, _ -> page(1, 1, 2, listOf(wire("a", "r1"), wire("a", "r2"))) })
        }
    }
}
