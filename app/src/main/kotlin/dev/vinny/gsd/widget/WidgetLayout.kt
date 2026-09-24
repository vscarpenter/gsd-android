package dev.vinny.gsd.widget

/** Each task row's height. Rows were about 23dp, too small to tap reliably (review finding 28). */
internal const val WIDGET_ROW_DP = 36f

/** The widget's own chrome: 12dp padding top and bottom, the header, and the "+N more" line. */
private const val WIDGET_CHROME_DP = 64f

/** How many task rows fit a widget [heightDp] tall: one at least, five at most (spec §12.1). */
internal fun widgetRows(heightDp: Float): Int =
    ((heightDp - WIDGET_CHROME_DP) / WIDGET_ROW_DP).toInt().coerceIn(1, 5)
