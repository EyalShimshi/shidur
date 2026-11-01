package io.github.eyalshimshi.shidur

import androidx.compose.ui.graphics.toArgb
import io.github.eyalshimshi.shidur.ui.theme.Lab
import io.github.eyalshimshi.shidur.ui.theme.TonalPalette
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedSchemeTest {

    private val stationColors = listOf(0xFF1E88E5, 0xFFF9A825, 0xFF2E7D32, 0xFFE53935, 0xFF455A64, 0xFF6750A4)
        .map { it.toInt() }

    @Test
    fun labRoundTripKeepsColors() {
        for (argb in stationColors) {
            val (lightness, chroma, hue) = Lab.lch(argb)
            val back = Lab.toArgbOrNull(lightness, chroma, Math.toRadians(hue))
            requireNotNull(back) { "in-gamut color reported out of gamut: ${Integer.toHexString(argb)}" }
            for (shift in listOf(16, 8, 0)) {
                val expected = (argb shr shift) and 0xFF
                val actual = (back shr shift) and 0xFF
                assertTrue("channel off for ${Integer.toHexString(argb)}", abs(expected - actual) <= 1)
            }
        }
    }

    @Test
    fun toneIsLightness() {
        for (argb in stationColors) {
            val (_, chroma, hue) = Lab.lch(argb)
            val palette = TonalPalette(hue, chroma.coerceIn(32.0, 56.0))
            for (tone in 10..90 step 10) {
                val (lightness, _, _) = Lab.lch(palette.tone(tone).toArgb())
                assertEquals("tone $tone of ${Integer.toHexString(argb)}", tone.toDouble(), lightness, 1.0)
            }
        }
    }

    @Test
    fun textRolesMeetContrastOnEverySeed() {
        for (argb in stationColors) {
            val (_, chroma, hue) = Lab.lch(argb)
            val palette = TonalPalette(hue, chroma.coerceIn(32.0, 56.0))
            // Light primary/onPrimary, dark primary/onPrimary, and both container pairs.
            for ((background, foreground) in listOf(40 to 100, 80 to 20, 90 to 10, 30 to 90)) {
                val ratio = contrast(palette.tone(background).toArgb(), palette.tone(foreground).toArgb())
                assertTrue("contrast $ratio for tones $background/$foreground", ratio >= 4.5)
            }
        }
    }

    @Test
    fun extremeTonesAreBlackAndWhite() {
        val palette = TonalPalette(270.0, 48.0)
        assertEquals(0xFF000000.toInt(), palette.tone(0).toArgb())
        assertEquals(0xFFFFFFFF.toInt(), palette.tone(100).toArgb())
    }

    private fun contrast(a: Int, b: Int): Double {
        val la = Lab.luminance(a)
        val lb = Lab.luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
