package dev.vinny.gsd.nav

import kotlin.test.Test
import kotlin.test.assertEquals

class NavLayoutTest {
    @Test
    fun `a phone in portrait gets the bottom bar`() = assertEquals(NavLayout.BAR, navLayoutFor(411, 891))

    @Test
    fun `a medium window gets the rail`() = assertEquals(NavLayout.RAIL, navLayoutFor(700, 900))

    @Test
    fun `a tablet gets the drawer`() = assertEquals(NavLayout.DRAWER, navLayoutFor(1280, 800))

    /** Review finding 36: a wide phone in landscape got the tablet drawer. */
    @Test
    fun `a wide phone in landscape gets the rail`() = assertEquals(NavLayout.RAIL, navLayoutFor(915, 411))
}
