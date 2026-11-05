package io.github.eyalshimshi.shidur.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.ThemeMode

@Composable
fun isDarkTheme(settings: AppSettings): Boolean = when (settings.themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Whether decorative motion (springs, pulses) is enabled in Settings. */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/** Whether Expert mode is on: technical details and experimental options are shown. */
val LocalExpertMode = staticCompositionLocalOf { false }

/** Accent colors offered in Settings. The first one is Material's baseline purple (the default). */
val ACCENT_PRESETS: List<Int> = listOf(
    0xFF6750A4.toInt(),
    0xFF0061A4.toInt(),
    0xFF006A6A.toInt(),
    0xFF386A20.toInt(),
    0xFF8B5000.toInt(),
    0xFF9C4146.toInt(),
    0xFFB4236C.toInt(),
    0xFF0038B8.toInt(),
)

/**
 * Wallpaper colors when dynamic color is on; otherwise a scheme generated from the chosen accent
 * (the Material 3 baseline scheme by default). Pure black keeps backgrounds black on OLED screens
 * while accents stay colored.
 */
@Composable
fun ShidurTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = isDarkTheme(settings)
    val context = LocalContext.current
    val accent = settings.accentColor
    val base = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor -> dynamicLightColorScheme(context)
        accent != null && accent != ACCENT_PRESETS.first() ->
            remember(accent, dark) { SeedScheme.colorScheme(Color(accent), dark) }
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    val scheme = if (dark && settings.pureBlack) base.withPureBlack() else base
    CompositionLocalProvider(
        LocalAnimationsEnabled provides settings.animations,
        LocalExpertMode provides settings.expertMode,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
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

/**
 * The player's colors, generated from the station's brand color with the same tones (and
 * therefore the same contrast) as the app's own scheme.
 */
fun stationColorScheme(stationColor: Int, dark: Boolean, pureBlack: Boolean): ColorScheme {
    val scheme = SeedScheme.colorScheme(Color(stationColor), dark)
    return if (dark && pureBlack) scheme.withPureBlack() else scheme
}
