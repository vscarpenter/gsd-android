package dev.vinny.gsd.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.vinny.gsd.model.Quadrant

/**
 * The full GSD token set (spec §5.1–§5.3). Hex values are authoritative from the spec, and
 * ContrastTest holds every text ink to WCAG AA on the surfaces it sits on. Text in a quadrant's
 * color uses [accentInk], never [accent]: ochre and slate are too light for text in light mode.
 */
@Immutable
data class GsdColors(
    val paper: Color,
    val sunken: Color,
    val surface: Color,
    val surface2: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val inkOnAccent: Color,
    val shadow: Color,
    val success: Color,
    val alert: Color,
    val alertWash: Color,
    val tint: Color,
    val rust: Color,
    val tide: Color,
    val ochre: Color,
    val slate: Color,
    val rustWash: Color,
    val tideWash: Color,
    val ochreWash: Color,
    val slateWash: Color,
    val ochreInk: Color,
    val slateInk: Color,
) {
    fun accent(quadrant: Quadrant): Color = when (quadrant) {
        Quadrant.DO_FIRST -> rust
        Quadrant.SCHEDULE -> tide
        Quadrant.DELEGATE -> ochre
        Quadrant.ELIMINATE -> slate
    }

    /** A quadrant's color for text, AA on paper and on its wash (review, Accessibility). */
    fun accentInk(quadrant: Quadrant): Color = when (quadrant) {
        Quadrant.DO_FIRST -> rust
        Quadrant.SCHEDULE -> tide
        Quadrant.DELEGATE -> ochreInk
        Quadrant.ELIMINATE -> slateInk
    }

    fun wash(quadrant: Quadrant): Color = when (quadrant) {
        Quadrant.DO_FIRST -> rustWash
        Quadrant.SCHEDULE -> tideWash
        Quadrant.DELEGATE -> ochreWash
        Quadrant.ELIMINATE -> slateWash
    }
}

val GsdColorsLight = GsdColors(
    paper = Color(0xFFF4F1E9),
    sunken = Color(0xFFECE7DC),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFFBF9F3),
    hairline = Color(0xFFE3DDD0),
    hairlineStrong = Color(0xFFD8D1C1),
    ink = Color(0xFF211E1A),
    ink2 = Color(0xFF5C5751),
    ink3 = Color(0xFF6A655C),
    inkOnAccent = Color(0xFFFFFFFF),
    shadow = Color(0xFF282116),
    success = Color(0xFF39724B),
    alert = Color(0xFFB23A2E),
    alertWash = Color(0xFFF4E4E0),
    tint = Color(0xFF2C6680),
    rust = Color(0xFFB23A2E),
    tide = Color(0xFF2C6680),
    ochre = Color(0xFF8A6A22),
    slate = Color(0xFF6F685F),
    rustWash = Color(0xFFF4E4E0),
    tideWash = Color(0xFFE1ECF1),
    ochreWash = Color(0xFFF0E9D8),
    slateWash = Color(0xFFECE9E3),
    ochreInk = Color(0xFF74591C),
    slateInk = Color(0xFF615B54),
)

val GsdColorsDark = GsdColors(
    paper = Color(0xFF17150F),
    sunken = Color(0xFF100E0A),
    surface = Color(0xFF221E17),
    surface2 = Color(0xFF1B1812),
    hairline = Color(0xFF322D24),
    hairlineStrong = Color(0xFF423B2F),
    ink = Color(0xFFF1ECE2),
    ink2 = Color(0xFFA79F92),
    ink3 = Color(0xFF948A79),
    inkOnAccent = Color(0xFF17150F),
    shadow = Color(0xFF000000),
    success = Color(0xFF6FB07F),
    alert = Color(0xFFE0705F),
    alertWash = Color(0xFF3A211D),
    tint = Color(0xFF6FAACB),
    rust = Color(0xFFE0705F),
    tide = Color(0xFF6FAACB),
    ochre = Color(0xFFCFB266),
    slate = Color(0xFFA9A096),
    rustWash = Color(0xFF3A211D),
    tideWash = Color(0xFF173039),
    ochreWash = Color(0xFF322B17),
    slateWash = Color(0xFF2A2620),
    ochreInk = Color(0xFFCFB266),
    slateInk = Color(0xFFA9A096),
)
