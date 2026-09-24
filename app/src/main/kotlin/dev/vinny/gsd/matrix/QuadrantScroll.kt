package dev.vinny.gsd.matrix

import dev.vinny.gsd.model.Quadrant

/**
 * The list index of [target]'s header in the phone's Matrix list, for `gsd://quadrant/<id>` and
 * Ctrl+1 to Ctrl+4 (spec §6.4, §6.6). [rows] gives each section's row count in list order. A section
 * is its header, then its rows, or one empty prompt when it has none.
 */
internal fun quadrantHeaderIndex(rows: List<Pair<Quadrant, Int>>, target: Quadrant): Int {
    var index = 0
    for ((quadrant, count) in rows) {
        if (quadrant == target) return index
        index += 1 + maxOf(count, 1)
    }
    return 0
}
