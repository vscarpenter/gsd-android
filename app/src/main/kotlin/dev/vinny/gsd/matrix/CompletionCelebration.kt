package dev.vinny.gsd.matrix

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.vinny.gsd.model.Task

/**
 * The one haptic and the one celebration in the app (spec §5.8), for every screen that completes
 * a task (§8.4). It lives in the composition and not in a view model. A view model outlives a tab
 * switch, and a trigger kept there would replay the confetti on the way back.
 */
@Stable
class CompletionCelebration(private val haptics: HapticFeedback) {
    /** Feeds [ConfettiOverlay]. Each increment plays the confetti once. */
    var confettiTrigger by mutableIntStateOf(0)
        private set

    /** Takes the task as the user saw it, before the toggle. Reopening a task celebrates nothing. */
    fun onToggle(task: Task) {
        if (task.completed) return
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        confettiTrigger++
    }
}

@Composable
fun rememberCompletionCelebration(): CompletionCelebration {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { CompletionCelebration(haptics) }
}
