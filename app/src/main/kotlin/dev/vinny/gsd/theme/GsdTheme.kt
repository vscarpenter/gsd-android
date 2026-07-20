package dev.vinny.gsd.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalGsdColors = staticCompositionLocalOf { GsdColorsLight }

/**
 * Material 3 mapping per spec §5.3 — no dynamic color, no tonal elevation
 * tinting (surfaceTint pinned to surface), no baseline purple anywhere.
 */
@Composable
fun GsdTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val gsd = if (darkTheme) GsdColorsDark else GsdColorsLight
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = gsd.tint,
            onPrimary = gsd.inkOnAccent,
            background = gsd.paper,
            onBackground = gsd.ink,
            surface = gsd.surface,
            onSurface = gsd.ink,
            surfaceVariant = gsd.sunken,
            onSurfaceVariant = gsd.ink2,
            outline = gsd.hairlineStrong,
            outlineVariant = gsd.hairline,
            error = gsd.alert,
            surfaceTint = gsd.surface,
            // surfaceContainer family + secondaryContainer feed NavigationBar/Rail,
            // sheets, and selection indicators — unmapped they resurface baseline purple
            secondaryContainer = gsd.sunken,
            onSecondaryContainer = gsd.ink,
            surfaceContainerLowest = gsd.paper,
            surfaceContainerLow = gsd.surface2,
            surfaceContainer = gsd.surface2,
            surfaceContainerHigh = gsd.surface,
            surfaceContainerHighest = gsd.sunken,
            // inverse family feeds Snackbar; unmapped inversePrimary is baseline purple
            inverseSurface = gsd.ink,
            inverseOnSurface = gsd.paper,
            inversePrimary = GsdColorsLight.tint,
        )
    } else {
        lightColorScheme(
            primary = gsd.tint,
            onPrimary = gsd.inkOnAccent,
            background = gsd.paper,
            onBackground = gsd.ink,
            surface = gsd.surface,
            onSurface = gsd.ink,
            surfaceVariant = gsd.sunken,
            onSurfaceVariant = gsd.ink2,
            outline = gsd.hairlineStrong,
            outlineVariant = gsd.hairline,
            error = gsd.alert,
            surfaceTint = gsd.surface,
            secondaryContainer = gsd.sunken,
            onSecondaryContainer = gsd.ink,
            surfaceContainerLowest = gsd.paper,
            surfaceContainerLow = gsd.surface2,
            surfaceContainer = gsd.surface2,
            surfaceContainerHigh = gsd.surface,
            surfaceContainerHighest = gsd.sunken,
            inverseSurface = gsd.ink,
            inverseOnSurface = gsd.paper,
            inversePrimary = GsdColorsDark.tint,
        )
    }
    CompositionLocalProvider(LocalGsdColors provides gsd) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
