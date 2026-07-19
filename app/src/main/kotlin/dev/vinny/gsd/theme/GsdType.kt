@file:OptIn(ExperimentalTextApi::class)

package dev.vinny.gsd.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.vinny.gsd.R

/**
 * Exactly two typefaces (spec §5.4): Newsreader for display roles only,
 * system sans for everything functional. Never body copy in serif.
 */
val Newsreader = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.newsreader, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.newsreader, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

object GsdType {
    val largeTitle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 34.sp)
    val title = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val quadrantTitle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
    val cardTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val body = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 17.sp)
    val subhead = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val metadata = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val caption = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp)
}
