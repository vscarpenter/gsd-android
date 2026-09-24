package dev.vinny.gsd.nav

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Spec 6.4: a deep link runs once. `singleTask` keeps the last link as the task's base intent, so a
 * launch from Recents after a reboot started the activity fresh and ran that old link again.
 */
class LaunchLinkTest {
    @Test
    fun aFreshLaunchRunsItsLink() = assertTrue(runsLaunchLink(recreated = false, intentFlags = 0))

    @Test
    fun aRecreationDoesNotRunItAgain() = assertFalse(runsLaunchLink(recreated = true, intentFlags = 0))

    @Test
    fun aLaunchFromRecentsDoesNotRunItAgain() =
        assertFalse(runsLaunchLink(recreated = false, intentFlags = LAUNCHED_FROM_HISTORY or 0x10000000))
}
