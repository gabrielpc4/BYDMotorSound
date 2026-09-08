package com.gabrielpc.enginesoundsimulator.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
fun EngineSoundsSimulatorTheme(
    skin: DashboardSkin,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(skin) { skin.materialColorScheme() }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/**
 * Material color scheme derived from the active skin.
 *
 * Stock Material components — sliders above all — read their colors from here instead of taking
 * explicit arguments, so without this they render in Material's default purple and ignore the
 * dashboard palette entirely.
 */
internal fun DashboardSkin.materialColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = accent,
        // Slider tick marks over the filled track use onPrimary, so it must stay dark.
        onPrimary = background,
        primaryContainer = surfaceRaised,
        onPrimaryContainer = onSurface,
        secondary = accentSoft,
        onSecondary = background,
        secondaryContainer = surfaceRaised,
        onSecondaryContainer = onSurface,
        tertiary = success,
        onTertiary = background,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        // Slider inactive tracks and unselected controls use the surface variant pair.
        surfaceVariant = surfaceRaised,
        onSurfaceVariant = muted,
        surfaceContainer = surface,
        surfaceContainerHigh = surfaceRaised,
        outline = outline,
        outlineVariant = outline,
        error = danger,
        onError = onSurface,
        errorContainer = errorBannerBody,
        onErrorContainer = onSurface,
        scrim = background,
    )
}
