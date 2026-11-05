package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.playback.LogoNormalizer.LogoLayout
import io.github.eyalshimshi.shidur.playback.LogoNormalizer.LogoLayout.Box
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How station logos are laid out on their square artwork (see LogoNormalizer). */
class LogoLayoutTest {

    private val transparent = 0x00000000
    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val purple = 0xFF51305F.toInt()
    private val blue = 0xFF0A57A8.toInt()
    private val dark = 0xFF1C1B1F.toInt()

    /** A [width] x [height] image of [background], with [paint] deciding any other pixel's color. */
    private fun image(width: Int, height: Int, background: Int, paint: (x: Int, y: Int) -> Int?): IntArray =
        IntArray(width * height) { i -> paint(i % width, i / width) ?: background }

    @Test
    fun solidTileFillsTheWholeSquareWithItsColor() {
        // A purple tile on a transparent canvas, like Galgalatz's logo.
        val pixels = image(120, 80, transparent) { x, y -> if (x in 4..115 && y in 4..75) purple else null }

        val layout = LogoLayout.of(pixels, 120, 80)!!

        assertEquals(purple, layout.fill)
        assertEquals(Box(4, 4, 116, 76), layout.box)
        assertEquals(0.06f, layout.margin, 0.001f)
        assertFalse(layout.cropSquare)
        assertNull(layout.clear)
    }

    @Test
    fun wordmarkLosesItsEmptyMarginsAndSitsOnWhite() {
        // Thin black strokes in the middle of a wide transparent canvas.
        val pixels = image(200, 100, transparent) { x, y -> if (y in 40..59 && x in 50..149 && x % 10 < 3) black else null }

        val layout = LogoLayout.of(pixels, 200, 100)!!

        assertEquals(white, layout.fill)
        assertEquals(Box(50, 40, 143, 60), layout.box)
        assertEquals(0.12f, layout.margin, 0.001f)
        assertNull(layout.clear)
    }

    @Test
    fun whiteWordmarkGetsADarkBackground() {
        val pixels = image(200, 100, transparent) { x, y -> if (y in 40..59 && x in 50..149 && x % 10 < 3) white else null }

        assertEquals(dark, LogoLayout.of(pixels, 200, 100)!!.fill)
    }

    @Test
    fun logoOnItsOwnBackgroundKeepsThatBackground() {
        // Blue strokes on an opaque white picture.
        val pixels = image(200, 100, white) { x, y -> if (y in 30..69 && x in 40..159 && x % 8 < 2) blue else null }

        val layout = LogoLayout.of(pixels, 200, 100)!!

        assertEquals(white, layout.fill)
        assertEquals(Box(40, 30, 154, 70), layout.box)
        assertNull(layout.clear)
    }

    @Test
    fun badgeOnABackgroundOfAnotherColorIsCutOut() {
        // A blue tile with a white letter inside, on an opaque white picture.
        val pixels = image(100, 100, white) { x, y ->
            when {
                x in 45..54 && y in 45..54 -> white
                x in 20..79 && y in 30..69 -> blue
                else -> null
            }
        }

        val layout = LogoLayout.of(pixels, 100, 100)!!

        assertEquals(blue, layout.fill)
        assertEquals(Box(20, 30, 80, 70), layout.box)
        val clear = assertNotNullAndGet(layout.clear)
        assertTrue("the picture's own background is removed", clear[0])
        assertFalse("the tile stays", clear[35 * 100 + 25])
        assertFalse("white inside the tile stays", clear[50 * 100 + 50])
    }

    @Test
    fun busySquarePictureIsUsedAsItIs() {
        val random = Random(1)
        val pixels = IntArray(100 * 100) { black or random.nextInt(0x1000000) }

        assertTrue(LogoLayout.of(pixels, 100, 100)!!.cropSquare)
    }

    @Test
    fun blankImagesHaveNoLayout() {
        assertNull(LogoLayout.of(IntArray(50 * 50), 50, 50))
        assertNull(LogoLayout.of(IntArray(50 * 50) { white }, 50, 50))
    }

    @Test
    fun aThinBorderLineIsIgnored() {
        assertEquals(2, LogoLayout.frameWidth(400, 200))
        assertEquals(1, LogoLayout.frameWidth(60, 60))
        assertEquals(0, LogoLayout.frameWidth(17, 17))
    }

    private fun <T : Any> assertNotNullAndGet(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
