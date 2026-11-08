package com.ithalo.hybridcoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val LocalRetroColors = staticCompositionLocalOf { retroColors(RetroPalette.BEGE, dark = false) }
val LocalRetroTypography = staticCompositionLocalOf { DefaultRetroTypography }

/** Entry point to the retro design tokens: `Retro.colors.ink`, `Retro.type.body`. */
object Retro {
    val colors: RetroColors
        @Composable @ReadOnlyComposable get() = LocalRetroColors.current
    val type: RetroTypography
        @Composable @ReadOnlyComposable get() = LocalRetroTypography.current
}

private val SquareShape = CutCornerShape(0.dp)
private val RetroShapes = Shapes(
    extraSmall = SquareShape,
    small = SquareShape,
    medium = SquareShape,
    large = SquareShape,
    extraLarge = SquareShape,
)

@Composable
fun HybridCoachTheme(
    palette: RetroPalette = RetroPalette.BEGE,
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val colors = remember(palette, dark) { retroColors(palette, dark) }
    val scheme = remember(colors) { colors.toColorScheme() }
    val typography = remember { DefaultRetroTypography.toMaterial() }
    CompositionLocalProvider(
        LocalRetroColors provides colors,
        LocalRetroTypography provides DefaultRetroTypography,
    ) {
        MaterialTheme(colorScheme = scheme, shapes = RetroShapes, typography = typography) {
            CompositionLocalProvider(LocalContentColor provides colors.ink, content = content)
        }
    }
}

/** Maps the retro slots onto Material roles so leftover Material widgets blend in (no tonal tint). */
fun RetroColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent2, onPrimary = onAccent2,
        primaryContainer = windowInset, onPrimaryContainer = ink,
        inversePrimary = if (isDark) mix(accent2, Color.Black, 0.3f) else mix(accent2, Color.White, 0.4f),
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = mix(window, accent, 0.18f), onSecondaryContainer = ink,
        tertiary = gold, onTertiary = if (isDark) background else ink,
        tertiaryContainer = window, onTertiaryContainer = ink,
        background = background, onBackground = ink,
        surface = window, onSurface = ink,
        surfaceVariant = windowInset, onSurfaceVariant = inkSoft,
        surfaceTint = Color.Transparent,
        inverseSurface = ink, inverseOnSurface = background,
        error = danger, onError = onDanger,
        errorContainer = mix(window, danger, 0.18f), onErrorContainer = ink,
        outline = border, outlineVariant = inkFaint,
        scrim = Color.Black,
        surfaceBright = if (isDark) mix(window, ink, 0.08f) else windowInset,
        surfaceDim = if (isDark) background else mix(background, ink, 0.06f),
        surfaceContainerLowest = if (isDark) background else windowInset,
        surfaceContainerLow = mix(window, if (isDark) background else windowInset, 0.5f),
        surfaceContainer = window,
        surfaceContainerHigh = if (isDark) mix(window, ink, 0.05f) else mix(window, background, 0.5f),
        surfaceContainerHighest = if (isDark) mix(window, ink, 0.09f) else background,
    )
}