package dev.vinny.gsd.nav

import androidx.navigation.NavController

/**
 * Tab-style navigation to a §6.1 destination: one copy on the stack, and the tab being left keeps
 * its own stack.
 *
 * Matrix is the start destination, so it is always at the bottom of the stack and "go to Matrix"
 * is a pop. It must not go through navigate(): Matrix is also the popUpTo target, so a restore
 * brings back the screen that same call just popped, and launchSingleTop swaps the visible entry
 * for a copy that never reaches STARTED.
 */
fun NavController.navigateToTab(destination: Destination) {
    if (destination == Destination.MATRIX) {
        popBackStack(Destination.MATRIX.route, inclusive = false, saveState = true)
        return
    }
    navigate(destination.route) {
        launchSingleTop = true
        popUpTo(Destination.MATRIX.route) { saveState = true }
        restoreState = true
    }
}

/** A §6.1 route behaves like its tab. Any other route pushes over the current screen. */
fun NavController.navigateToRoute(route: String) {
    val tab = Destination.entries.firstOrNull { it.route == route }
    if (tab != null) navigateToTab(tab) else navigate(route)
}
