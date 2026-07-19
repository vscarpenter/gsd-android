package dev.vinny.gsd.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii (spec §5.5). Chips, buttons, capture bar are full pills. */
object GsdShapes {
    val tile = RoundedCornerShape(8.dp)
    val small = RoundedCornerShape(12.dp)
    val input = RoundedCornerShape(16.dp)
    val card = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(26.dp)
}
