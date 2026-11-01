package io.github.eyalshimshi.shidur.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds Material 3 color schemes from a single seed color (an accent pick or a station's brand
 * color). Colors are generated in CIELAB, whose lightness L* is the same "tone" scale Material
 * uses, so every role keeps Material's contrast guarantees whatever the seed.
 */
object SeedScheme {

    fun colorScheme(seed: Color, dark: Boolean): ColorScheme {
        val (_, seedChroma, seedHue) = Lab.lch(seed.toArgb())
        // Gray-ish seeds (black, white or gray brands) give calm schemes rather than a random hue.
        val colorfulness = (seedChroma / FULL_COLOR_CHROMA).coerceIn(0.0, 1.0)
        val primary = TonalPalette(seedHue, seedChroma.coerceIn(32.0, 56.0) * colorfulness)
        val secondary = TonalPalette(seedHue, 18.0 * colorfulness)
        val tertiary = TonalPalette(seedHue + TERTIARY_HUE_SHIFT, 28.0 * colorfulness)
        val neutral = TonalPalette(seedHue, 5.0 * colorfulness)
        val neutralVariant = TonalPalette(seedHue, 10.0 * colorfulness)
        val error = TonalPalette(ERROR_HUE, 62.0)
        return if (dark) {
            darkColorScheme(
                primary = primary.tone(80),
                onPrimary = primary.tone(20),
                primaryContainer = primary.tone(30),
                onPrimaryContainer = primary.tone(90),
                inversePrimary = primary.tone(40),
                secondary = secondary.tone(80),
                onSecondary = secondary.tone(20),
                secondaryContainer = secondary.tone(30),
                onSecondaryContainer = secondary.tone(90),
                tertiary = tertiary.tone(80),
                onTertiary = tertiary.tone(20),
                tertiaryContainer = tertiary.tone(30),
                onTertiaryContainer = tertiary.tone(90),
                background = neutral.tone(6),
                onBackground = neutral.tone(90),
                surface = neutral.tone(6),
                onSurface = neutral.tone(90),
                surfaceVariant = neutralVariant.tone(30),
                onSurfaceVariant = neutralVariant.tone(80),
                surfaceTint = primary.tone(80),
                inverseSurface = neutral.tone(90),
                inverseOnSurface = neutral.tone(20),
                error = error.tone(80),
                onError = error.tone(20),
                errorContainer = error.tone(30),
                onErrorContainer = error.tone(90),
                outline = neutralVariant.tone(60),
                outlineVariant = neutralVariant.tone(30),
                scrim = Color.Black,
                surfaceBright = neutral.tone(24),
                surfaceContainer = neutral.tone(12),
                surfaceContainerHigh = neutral.tone(17),
                surfaceContainerHighest = neutral.tone(22),
                surfaceContainerLow = neutral.tone(10),
                surfaceContainerLowest = neutral.tone(4),
                surfaceDim = neutral.tone(6),
            )
        } else {
            lightColorScheme(
                primary = primary.tone(40),
                onPrimary = primary.tone(100),
                primaryContainer = primary.tone(90),
                onPrimaryContainer = primary.tone(10),
                inversePrimary = primary.tone(80),
                secondary = secondary.tone(40),
                onSecondary = secondary.tone(100),
                secondaryContainer = secondary.tone(90),
                onSecondaryContainer = secondary.tone(10),
                tertiary = tertiary.tone(40),
                onTertiary = tertiary.tone(100),
                tertiaryContainer = tertiary.tone(90),
                onTertiaryContainer = tertiary.tone(10),
                background = neutral.tone(98),
                onBackground = neutral.tone(10),
                surface = neutral.tone(98),
                onSurface = neutral.tone(10),
                surfaceVariant = neutralVariant.tone(90),
                onSurfaceVariant = neutralVariant.tone(30),
                surfaceTint = primary.tone(40),
                inverseSurface = neutral.tone(20),
                inverseOnSurface = neutral.tone(95),
                error = error.tone(40),
                onError = error.tone(100),
                errorContainer = error.tone(90),
                onErrorContainer = error.tone(10),
                outline = neutralVariant.tone(50),
                outlineVariant = neutralVariant.tone(80),
                scrim = Color.Black,
                surfaceBright = neutral.tone(98),
                surfaceContainer = neutral.tone(94),
                surfaceContainerHigh = neutral.tone(92),
                surfaceContainerHighest = neutral.tone(90),
                surfaceContainerLow = neutral.tone(96),
                surfaceContainerLowest = neutral.tone(100),
                surfaceDim = neutral.tone(87),
            )
        }
    }

    private const val TERTIARY_HUE_SHIFT = 60.0
    private const val ERROR_HUE = 33.0

    /** Seeds at least this colorful (CIELAB chroma) get fully colored schemes. */
    private const val FULL_COLOR_CHROMA = 16.0
}

/** Colors of one hue and chroma at any lightness ("tone" 0–100), clipped to the sRGB gamut. */
class TonalPalette(hueDegrees: Double, private val chroma: Double) {

    private val hue = Math.toRadians(hueDegrees)
    private val cache = HashMap<Int, Color>()

    fun tone(tone: Int): Color = cache.getOrPut(tone) { Color(argbAt(tone.toDouble())) }

    private fun argbAt(lightness: Double): Int {
        if (lightness <= 0.0) return BLACK
        if (lightness >= 100.0) return WHITE
        Lab.toArgbOrNull(lightness, chroma, hue)?.let { return it }
        // Out of gamut: keep the lightness (and therefore the contrast) and lower the chroma.
        var low = 0.0
        var high = chroma
        var best = Lab.toArgbOrNull(lightness, 0.0, hue) ?: gray(lightness)
        repeat(GAMUT_SEARCH_STEPS) {
            val mid = (low + high) / 2
            val argb = Lab.toArgbOrNull(lightness, mid, hue)
            if (argb != null) {
                best = argb
                low = mid
            } else {
                high = mid
            }
        }
        return best
    }

    private fun gray(lightness: Double): Int = Lab.toArgbOrNull(lightness, 0.0, 0.0) ?: BLACK

    private companion object {
        const val GAMUT_SEARCH_STEPS = 14
        const val BLACK = 0xFF000000.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}

/** sRGB ⇄ CIELAB (D65) conversions. */
object Lab {

    private const val XN = 0.95047
    private const val YN = 1.0
    private const val ZN = 1.08883
    private const val EPSILON = 216.0 / 24389.0
    private const val KAPPA = 24389.0 / 27.0

    /** Returns (lightness, chroma, hue in degrees) of an ARGB color. */
    fun lch(argb: Int): Triple<Double, Double, Double> {
        val r = linear((argb shr 16) and 0xFF)
        val g = linear((argb shr 8) and 0xFF)
        val b = linear(argb and 0xFF)
        val x = 0.4124564 * r + 0.3575761 * g + 0.1804375 * b
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
        val z = 0.0193339 * r + 0.1191920 * g + 0.9503041 * b
        val fx = f(x / XN)
        val fy = f(y / YN)
        val fz = f(z / ZN)
        val lightness = 116.0 * fy - 16.0
        val a = 500.0 * (fx - fy)
        val bb = 200.0 * (fy - fz)
        val hue = Math.toDegrees(atan2(bb, a)).let { if (it < 0) it + 360.0 else it }
        return Triple(lightness, hypot(a, bb), hue)
    }

    /** The ARGB color at (lightness, chroma, hue in radians), or null if it is outside sRGB. */
    fun toArgbOrNull(lightness: Double, chroma: Double, hueRadians: Double): Int? {
        val a = chroma * cos(hueRadians)
        val b = chroma * sin(hueRadians)
        val fy = (lightness + 16.0) / 116.0
        val fx = fy + a / 500.0
        val fz = fy - b / 200.0
        val x = XN * fInverse(fx)
        val y = YN * (if (lightness > KAPPA * EPSILON) fy * fy * fy else lightness / KAPPA)
        val z = ZN * fInverse(fz)
        val r = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
        val g = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
        val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
        if (!inGamut(r) || !inGamut(g) || !inGamut(bl)) return null
        return (0xFF shl 24) or (encode(r) shl 16) or (encode(g) shl 8) or encode(bl)
    }

    /** WCAG relative luminance of an ARGB color. */
    fun luminance(argb: Int): Double =
        0.2126 * linear((argb shr 16) and 0xFF) +
            0.7152 * linear((argb shr 8) and 0xFF) +
            0.0722 * linear(argb and 0xFF)

    private fun inGamut(value: Double) = value >= -GAMUT_TOLERANCE && value <= 1.0 + GAMUT_TOLERANCE

    private fun f(t: Double) = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16.0) / 116.0

    private fun fInverse(t: Double): Double {
        val cube = t * t * t
        return if (cube > EPSILON) cube else (116.0 * t - 16.0) / KAPPA
    }

    private fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun encode(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val c = if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055
        return (c * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    private const val GAMUT_TOLERANCE = 0.0005
}
