package io.github.eyalshimshi.shidur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.ThemeMode

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Dynamic color from the wallpaper when enabled, otherwise the Material 3 baseline scheme
 * (seed #6750A4). Pure black replaces dark surfaces with black for OLED screens.
 */
@Composable
fun ShidurTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && settings.pureBlack) base.withPureBlack() else base
    MaterialTheme(colorScheme = scheme, content = content)
}

fun ColorScheme.withPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0C),
    surfaceContainer = Color(0xFF111114),
    surfaceContainerHigh = Color(0xFF18181B),
    surfaceContainerHighest = Color(0xFF1F1F23),
)

/** Tints a scheme toward a station's color for the full-screen player. */
fun ColorScheme.tintedWith(seed: Color, dark: Boolean, keepBlackBackground: Boolean): ColorScheme {
    val tintedPrimary = if (dark) lerp(seed, Color.White, 0.35f) else lerp(seed, Color.Black, 0.2f)
    val tintedContainer = if (dark) lerp(seed, Color.Black, 0.45f) else lerp(seed, Color.White, 0.7f)
    val tintedBackground = if (keepBlackBackground) background else lerp(background, seed, if (dark) 0.16f else 0.1f)
    return copy(
        primary = tintedPrimary,
        onPrimary = readableOn(tintedPrimary),
        primaryContainer = tintedContainer,
        onPrimaryContainer = readableOn(tintedContainer),
        secondaryContainer = tintedContainer,
        onSecondaryContainer = readableOn(tintedContainer),
        background = tintedBackground,
        surface = tintedBackground,
        surfaceContainer = lerp(surfaceContainer, seed, 0.12f),
        surfaceContainerHigh = lerp(surfaceContainerHigh, seed, 0.14f),
    )
}

fun readableOn(color: Color): Color = if (color.luminance() > 0.45f) Color(0xFF1C1B1F) else Color.White
