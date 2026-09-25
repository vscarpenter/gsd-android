package dev.vinny.gsd.widget

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Review finding 28: widget rows were about 23 dp tall. They are now 36 dp, and the widget shows as
 * many as its height fits, one at least and five at most (spec 12.1).
 */
class WidgetRowsTest {
    @Test
    fun aTwoCellWidgetShowsThree() = assertEquals(3, widgetRows(heightDp = 182f))

    @Test
    fun aShortWidgetStillShowsOne() = assertEquals(1, widgetRows(heightDp = 90f))

    @Test
    fun aTallWidgetStopsAtFive() = assertEquals(5, widgetRows(heightDp = 400f))

    @Test
    fun eachRowGetsItsFullHeight() {
        assertEquals(2, widgetRows(heightDp = 64f + 36f * 2))
        assertEquals(1, widgetRows(heightDp = 64f + 36f * 2 - 1f))
    }
}
