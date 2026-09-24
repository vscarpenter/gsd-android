package dev.vinny.gsd.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight

val LocalGsdColors = staticCompositionLocalOf { GsdColorsLight }

/** The text caret, in Tide. BasicTextField's default is black, which vanishes on dark paper (finding 10). */
val GsdColors.cursor: Brush get() = SolidColor(tint)

/**
 * Material 3 mapping per spec §5.3 — no dynamic color, no tonal elevation
 * tinting (surfaceTint pinned to surface), no baseline purple anywhere.
 * Shapes and type come from §5.4 and §5.5, so stock components match the
 * hand-built ones (review finding 24).
 */
@Composable
fun GsdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val gsd = if (darkTheme) GsdColorsDark else GsdColorsLight
    CompositionLocalProvider(LocalGsdColors provides gsd) {
        MaterialTheme(
            colorScheme = gsdColorScheme(gsd, darkTheme),
            shapes = GsdMaterialShapes,
            typography = GsdMaterialTypography,
            content = content,
        )
    }
}

/**
 * Every role a stock component reads comes from the GSD tokens. An unmapped role keeps Material's
 * baseline purple: `secondary` colored the selected navigation label (review finding 23), and the
 * date and time pickers read the container roles.
 */
private fun gsdColorScheme(gsd: GsdColors, dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = gsd.tint,
        onPrimary = gsd.inkOnAccent,
        primaryContainer = gsd.tideWash,
        onPrimaryContainer = gsd.ink,
        secondary = gsd.tint,
        onSecondary = gsd.inkOnAccent,
        // secondaryContainer feeds the NavigationBar/Rail selection indicator
        secondaryContainer = gsd.sunken,
        onSecondaryContainer = gsd.ink,
        tertiary = gsd.tint,
        onTertiary = gsd.inkOnAccent,
        tertiaryContainer = gsd.tideWash,
        onTertiaryContainer = gsd.ink,
        background = gsd.paper,
        onBackground = gsd.ink,
        surface = gsd.surface,
        onSurface = gsd.ink,
        surfaceVariant = gsd.sunken,
        onSurfaceVariant = gsd.ink2,
        surfaceTint = gsd.surface,
        surfaceBright = gsd.surface,
        surfaceDim = gsd.sunken,
        // the surfaceContainer family feeds navigation, sheets, menus, and dialogs
        surfaceContainerLowest = gsd.paper,
        surfaceContainerLow = gsd.surface2,
        surfaceContainer = gsd.surface2,
        surfaceContainerHigh = gsd.surface,
        surfaceContainerHighest = gsd.sunken,
        // the inverse family feeds Snackbar
        inverseSurface = gsd.ink,
        inverseOnSurface = gsd.paper,
        inversePrimary = if (dark) GsdColorsLight.tint else GsdColorsDark.tint,
        outline = gsd.hairlineStrong,
        outlineVariant = gsd.hairline,
        error = gsd.alert,
        onError = gsd.inkOnAccent,
        errorContainer = gsd.alertWash,
        onErrorContainer = gsd.alert,
    )

/** Material's shape scale in §5.5 terms: chips are full pills; menus and snackbars round at 12dp. */
internal val GsdMaterialShapes = Shapes(
    extraSmall = GsdShapes.small,
    small = RoundedCornerShape(percent = 50),
    medium = GsdShapes.input,
    large = GsdShapes.card,
    extraLarge = GsdShapes.sheet,
)

/** Material's type scale in §5.4 terms: Newsreader for display roles, the system sans for the rest. */
internal val GsdMaterialTypography = Typography(
    displayLarge = GsdType.largeTitle,
    displayMedium = GsdType.largeTitle,
    displaySmall = GsdType.title,
    headlineLarge = GsdType.largeTitle,
    headlineMedium = GsdType.title,
    headlineSmall = GsdType.cardTitle, // dialog titles, as the app's own dialogs set them
    titleLarge = GsdType.quadrantTitle,
    titleMedium = GsdType.cardTitle,
    titleSmall = GsdType.subhead.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = GsdType.body,
    bodyMedium = GsdType.subhead,
    bodySmall = GsdType.metadata,
    labelLarge = GsdType.subhead.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = GsdType.metadata.copy(fontWeight = FontWeight.Medium),
    labelSmall = GsdType.caption,
)
