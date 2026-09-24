package dev.vinny.gsd.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/**
 * Lets a list item slide to its new place and fade in or out on the default spring (spec §5.8), so
 * a completed task moves instead of jumping (review finding 33). When the system turns animations
 * off, items snap into place: the one reduced-motion gate (§13.3).
 */
fun LazyItemScope.reorderMotion(): Modifier =
    if (ValueAnimator.areAnimatorsEnabled()) Modifier.animateItem() else Modifier
