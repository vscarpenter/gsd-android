package dev.vinny.gsd.nav

import dev.vinny.gsd.model.Quadrant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GsdRouteTest {
    @Test
    fun `the full spec 6-4 route table parses`() {
        assertEquals(GsdRoute.Capture, GsdRoute.parse("gsd://capture"))
        assertEquals(GsdRoute.Focus, GsdRoute.parse("gsd://focus"))
        assertEquals(
            GsdRoute.QuadrantLink(Quadrant.DELEGATE),
            GsdRoute.parse("gsd://quadrant/urgent-not-important"),
        )
        assertEquals(GsdRoute.TaskLink("abc123XYZ-_456789012a"), GsdRoute.parse("gsd://task/abc123XYZ-_456789012a"))
        assertEquals(GsdRoute.SmartViewLink("today-focus"), GsdRoute.parse("gsd://smart-view/today-focus"))
        assertEquals(GsdRoute.Dashboard, GsdRoute.parse("gsd://dashboard"))
        assertEquals(GsdRoute.Settings, GsdRoute.parse("gsd://settings"))
        assertEquals(GsdRoute.Archive, GsdRoute.parse("gsd://archive"))
    }

    @Test
    fun `oauth-callback is reserved for auth and ignored by the router`() {
        assertNull(GsdRoute.parse("gsd://oauth-callback?code=x&state=y"))
    }

    @Test
    fun `junk yields null`() {
        assertNull(GsdRoute.parse("gsd://unknown-surface"))
        assertNull(GsdRoute.parse("gsd://quadrant/not-a-quadrant"))
        assertNull(GsdRoute.parse("gsd://task/ab")) // below the 4-char id floor
        assertNull(GsdRoute.parse("https://gsdtaskmanager.com/task/abcd"))
        assertNull(GsdRoute.parse("not a uri"))
    }
}
