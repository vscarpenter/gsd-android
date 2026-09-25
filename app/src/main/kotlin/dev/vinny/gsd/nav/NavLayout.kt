package dev.vinny.gsd.nav

/** Which navigation chrome a window gets (spec §6.1, §6.2). */
enum class NavLayout { BAR, RAIL, DRAWER }

/**
 * Material's width classes, with one rule for height: the drawer is for windows at least 840dp wide
 * and 480dp tall. A phone in landscape is wide but short, and it got the tablet drawer (finding 36).
 */
fun navLayoutFor(widthDp: Int, heightDp: Int): NavLayout = when {
    widthDp < 600 -> NavLayout.BAR
    widthDp < 840 || heightDp < 480 -> NavLayout.RAIL
    else -> NavLayout.DRAWER
}
