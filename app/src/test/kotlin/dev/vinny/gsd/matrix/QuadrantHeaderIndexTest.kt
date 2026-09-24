package dev.vinny.gsd.matrix

import dev.vinny.gsd.model.Quadrant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec 6.4 and 6.6: `gsd://quadrant/<id>` and Ctrl+1 to Ctrl+4 scroll the phone's Matrix list to
 * that quadrant's header. Each section is a header, then its rows or one empty prompt.
 */
class QuadrantHeaderIndexTest {
    private val rows = listOf(
        Quadrant.DO_FIRST to 3,
        Quadrant.SCHEDULE to 0, // an empty section still holds its prompt
        Quadrant.DELEGATE to 2,
        Quadrant.ELIMINATE to 1,
    )

    @Test
    fun doFirstIsTheTop() = assertEquals(0, quadrantHeaderIndex(rows, Quadrant.DO_FIRST))

    @Test
    fun eachHeaderFollowsTheRowsAbove() {
        assertEquals(4, quadrantHeaderIndex(rows, Quadrant.SCHEDULE)) // header + 3 rows
        assertEquals(6, quadrantHeaderIndex(rows, Quadrant.DELEGATE)) // + header + prompt
        assertEquals(9, quadrantHeaderIndex(rows, Quadrant.ELIMINATE)) // + header + 2 rows
    }
}
