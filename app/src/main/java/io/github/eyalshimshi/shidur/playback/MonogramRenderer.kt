package io.github.eyalshimshi.shidur.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import io.github.eyalshimshi.shidur.model.Station

/**
 * Draws a station's text artwork: its short name on its brand color. Used when a station has no
 * downloaded logo (see StationLogos).
 */
object MonogramRenderer {

    /**
     * Draws [station]'s monogram on a square bitmap. [contentScale] below 1 keeps the text in the
     * middle, e.g. for launcher icons that the launcher crops to a circle.
     */
    fun render(station: Station, sizePx: Int, contentScale: Float = 1f): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(station.color)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = contentColorFor(station.color)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = sizePx * fontScaleFor(station.shortName) * contentScale
        }
        val maxWidth = sizePx * 0.82f * contentScale
        val measured = paint.measureText(station.shortName)
        if (measured > maxWidth) paint.textSize = paint.textSize * maxWidth / measured
        val metrics = paint.fontMetrics
        val baseline = sizePx / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(station.shortName, sizePx / 2f, baseline, paint)
        return bitmap
    }

    /** Relative font size for a short name, so "88" is big and "מוזיקה" still fits. */
    fun fontScaleFor(text: String): Float = when (text.length) {
        0, 1, 2 -> 0.42f
        3 -> 0.34f
        4 -> 0.28f
        5 -> 0.24f
        else -> 0.2f
    }

    fun contentColorFor(argb: Int): Int =
        if (Color.luminance(argb) > 0.5f) 0xFF1D1B20.toInt() else Color.WHITE
}
