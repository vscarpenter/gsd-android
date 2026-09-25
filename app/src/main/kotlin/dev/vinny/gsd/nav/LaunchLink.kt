package dev.vinny.gsd.nav

/** `Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`: the system sets it on a launch from Recents. */
internal const val LAUNCHED_FROM_HISTORY = 0x00100000

/**
 * Whether a starting activity runs its launch intent's link (spec §6.4). A recreation already ran
 * it. So did a launch from Recents: `singleTask` keeps the last link as the task's base intent, and
 * after a reboot Recents starts the activity fresh with that old link, sign-in callback included.
 */
internal fun runsLaunchLink(recreated: Boolean, intentFlags: Int): Boolean =
    !recreated && intentFlags and LAUNCHED_FROM_HISTORY == 0
