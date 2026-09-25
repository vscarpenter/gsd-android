package dev.vinny.gsd.matrix

/**
 * Font scales from here up lift the card title's two-line cap (spec §5.4). Android 14 added 1.5,
 * 1.8, and 2.0 above the old 1.3 maximum for people with low vision. They are the counterpart of
 * the iOS accessibility sizes, where the iOS card lifts the same cap.
 */
internal const val ACCESSIBILITY_FONT_SCALE = 1.5f

/** Two lines at ordinary font scales, and no limit at accessibility scales (review finding 35). */
internal fun cardTitleMaxLines(fontScale: Float): Int =
    if (fontScale >= ACCESSIBILITY_FONT_SCALE) Int.MAX_VALUE else 2
