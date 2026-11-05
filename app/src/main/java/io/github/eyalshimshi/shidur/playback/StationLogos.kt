package io.github.eyalshimshi.shidur.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.eyalshimshi.shidur.data.USER_AGENT
import io.github.eyalshimshi.shidur.model.Station
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Station logos. Each is downloaded once from the address in the catalog, checked (images too
 * small to look good are skipped, so the station keeps its text artwork), made square and saved
 * on the device for the app, the notification, Android Auto and launcher shortcuts.
 */
class StationLogos(
    context: Context,
    client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val directory = File(context.filesDir, DIRECTORY)
    private val client = client.newBuilder().callTimeout(15, TimeUnit.SECONDS).build()
    private val memory = object : LruCache<String, ImageBitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val permits = Semaphore(MAX_PARALLEL)
    private val _saved = MutableStateFlow(0)

    /** Grows whenever a new logo is saved, so artwork shown elsewhere can be refreshed. */
    val saved: StateFlow<Int> = _saved.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) { deleteOutdated() }
    }

    /** The logo if it is already in memory; never touches the disk or the network. */
    fun cached(station: Station): ImageBitmap? = key(station)?.let { memory.get(it) }

    /** The saved logo file, once this station's logo has been downloaded. */
    fun file(station: Station): File? {
        val key = key(station) ?: return null
        return File(directory, "$key.png").takeIf { it.exists() }
    }

    /** The station's logo, downloading it the first time; null when it has no (good) logo. */
    suspend fun load(station: Station): ImageBitmap? {
        val url = station.logo ?: return null
        val key = key(station) ?: return null
        memory.get(key)?.let { return it }
        val fresh = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                permits.withPermit { loadBitmap(key, url) }
            } finally {
                inFlight.remove(key)
            }
        }
        val existing = inFlight.putIfAbsent(key, fresh)
        if (existing != null) {
            fresh.cancel()
            return existing.await()
        }
        fresh.start()
        return fresh.await()
    }

    private fun loadBitmap(key: String, url: String): ImageBitmap? {
        val file = File(directory, "$key.png")
        val bitmap = if (file.exists()) BitmapFactory.decodeFile(file.path) else download(key, url, file)
        val image = bitmap?.asImageBitmap() ?: return null
        memory.put(key, image)
        return image
    }

    private fun download(key: String, url: String, target: File): Bitmap? {
        if (File(directory, "$key.none").exists()) return null
        val lastFailure = failedAt[key]
        if (lastFailure != null && System.currentTimeMillis() - lastFailure < RETRY_AFTER_MS) return null

        val bytes = try {
            fetch(url)
        } catch (e: IOException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
        val side = bytes?.let(LogoNormalizer::smallerSide) ?: 0
        if (bytes == null || side == 0) {
            // Offline, or not an image at all (e.g. a Wi-Fi sign-in page): try again later.
            failedAt[key] = System.currentTimeMillis()
            return null
        }
        directory.mkdirs()
        if (side < MIN_SIDE_PX) {
            // A real image, but too small to look good: keep the text artwork for this address.
            File(directory, "$key.none").createNewFile()
            return null
        }
        val logo = LogoNormalizer.normalize(bytes, OUTPUT_PX)
        if (logo == null) {
            // A readable but blank image: keep the text artwork for this address.
            File(directory, "$key.none").createNewFile()
            return null
        }
        val temp = File(directory, "$key.tmp")
        FileOutputStream(temp).use { logo.compress(Bitmap.CompressFormat.PNG, 100, it) }
        temp.renameTo(target)
        _saved.update { it + 1 }
        return logo
    }

    private fun fetch(url: String): ByteArray? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val source = response.body?.source() ?: return null
            // Far larger than any logo: skip it rather than fill memory.
            if (source.request(MAX_DOWNLOAD_BYTES + 1L)) return null
            return source.readByteArray()
        }
    }

    /**
     * One file per station, logo address and way of drawing logos, so a changed address or a new
     * [LogoNormalizer.VERSION] makes the logo again.
     */
    private fun key(station: Station): String? =
        station.logo?.let { "${station.id}-${it.hashCode().toUInt().toString(16)}-n${LogoNormalizer.VERSION}" }

    /** Deletes logos saved by an older version of [LogoNormalizer]. */
    private fun deleteOutdated() {
        val current = "-n${LogoNormalizer.VERSION}."
        directory.listFiles()?.forEach { file -> if (!file.name.contains(current)) file.delete() }
    }

    private companion object {
        const val DIRECTORY = "logos"
        const val OUTPUT_PX = 384
        const val MIN_SIDE_PX = 96
        const val MEMORY_BYTES = 12 * 1024 * 1024
        const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024
        const val MAX_PARALLEL = 3
        const val RETRY_AFTER_MS = 6 * 60 * 60 * 1000L
    }
}

/**
 * Turns a downloaded logo into square artwork:
 * - a logo that is a solid shape (a tile or badge, like Galgalatz's purple tile) fills the whole
 *   square with its own color;
 * - any other logo loses its empty margins and is centered on its own background color, or on
 *   white (dark for white logos) when it has none;
 * - a square picture with a busy edge (a photo, a full-bleed icon) is used as it is.
 */
internal object LogoNormalizer {

    /** Raise whenever the artwork changes, so logos saved by older versions are made again. */
    const val VERSION = 2

    private const val BADGE_MARGIN = 0.06f
    private const val LOGO_MARGIN = 0.12f
    private const val SQUARE_MIN_ASPECT = 0.8f
    private const val SQUARE_MAX_ASPECT = 1.25f
    private const val DARK_BACKGROUND = 0xFF1C1B1F.toInt()

    /** The smaller side of the image in [bytes], in pixels; 0 when it isn't a readable image. */
    fun smallerSide(bytes: ByteArray): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) minOf(bounds.outWidth, bounds.outHeight) else 0
    }

    /** Square artwork of [outputPx] pixels; null when the image is unreadable or blank. */
    fun normalize(bytes: ByteArray, outputPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= outputPx) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            // Some logos come with a thin border line; it shouldn't decide the background.
            val frame = LogoLayout.frameWidth(source.width, source.height)
            val width = source.width - 2 * frame
            val height = source.height - 2 * frame
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, frame, frame, width, height)
            val layout = LogoLayout.of(pixels, width, height) ?: return null
            layout.clear?.let { clear -> for (i in pixels.indices) if (clear[i]) pixels[i] = 0 }
            val image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            try {
                return draw(image, layout, outputPx)
            } finally {
                image.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    private fun draw(image: Bitmap, layout: LogoLayout, outputPx: Int): Bitmap {
        val output = Bitmap.createBitmap(outputPx, outputPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (layout.cropSquare) {
            val side = minOf(image.width, image.height)
            val left = (image.width - side) / 2
            val top = (image.height - side) / 2
            canvas.drawBitmap(image, Rect(left, top, left + side, top + side), Rect(0, 0, outputPx, outputPx), paint)
            return output
        }
        canvas.drawColor(layout.fill)
        val box = layout.box
        val room = outputPx * (1f - 2f * layout.margin)
        val scale = minOf(room / box.width, room / box.height)
        val width = box.width * scale
        val height = box.height * scale
        val left = (outputPx - width) / 2f
        val top = (outputPx - height) / 2f
        canvas.drawBitmap(
            image,
            Rect(box.left, box.top, box.right, box.bottom),
            RectF(left, top, left + width, top + height),
            paint,
        )
        return output
    }

    /** How a logo is laid out on its square; worked out from its pixels alone, so it can be tested. */
    internal class LogoLayout(
        /** Use a centered square of the whole image as it is. */
        val cropSquare: Boolean,
        /** The part of the image to draw, centered. */
        val box: Box,
        /** The square's background color. */
        val fill: Int,
        /** Space left around [box] on each side, as a share of the square. */
        val margin: Float,
        /** Pixels to make transparent first (the logo's own background around a badge), or null. */
        val clear: BooleanArray?,
    ) {
        /** A rectangle of pixels; [right] and [bottom] are exclusive. */
        data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
            val width: Int get() = right - left
            val height: Int get() = bottom - top
        }

        private enum class Edge { TRANSPARENT, SOLID, BUSY }

        companion object {
            private const val FRAME_FRACTION = 0.01f
            private const val TRANSPARENT_EDGE_SHARE = 0.02f
            private const val SOLID_EDGE_SHARE = 0.9f
            private const val BADGE_SHARE = 0.8f
            private const val RING_INSET = 0.03f
            private const val LIGHT_LOGO_LUMINANCE = 0.82f

            // Squared distances between RGB colors.
            private const val SAME_EDGE = 32 * 32
            private const val SAME_BACKGROUND = 48 * 48
            private const val SAME_FILL = 40 * 40

            /** Pixels to ignore along each side of an image, so a thin border line doesn't count. */
            fun frameWidth(width: Int, height: Int): Int {
                val frame = maxOf(1, (minOf(width, height) * FRAME_FRACTION).roundToInt())
                return if (width - 2 * frame < MIN_INNER_PX || height - 2 * frame < MIN_INNER_PX) 0 else frame
            }

            private const val MIN_INNER_PX = 16

            /** The layout for an image given as ARGB [pixels], row by row; null when it is blank. */
            fun of(pixels: IntArray, width: Int, height: Int): LogoLayout? {
                if (width <= 0 || height <= 0 || pixels.size < width * height) return null
                val border = borderIndices(width, height)
                val outside = BooleanArray(width * height)
                var background = WHITE
                val edge = if (border.count { alpha(pixels[it]) < 250 } > border.size * TRANSPARENT_EDGE_SHARE) {
                    for (i in outside.indices) outside[i] = alpha(pixels[i]) < 32
                    Edge.TRANSPARENT
                } else {
                    background = medianColor(pixels, border)
                    if (border.count { distance2(pixels[it], background) <= SAME_EDGE } >= border.size * SOLID_EDGE_SHARE) {
                        floodFill(pixels, width, border, background, outside)
                        Edge.SOLID
                    } else {
                        Edge.BUSY
                    }
                }
                val box = contentBox(outside, width, height) ?: return null
                val ring = ringColor(pixels, width, outside, box)
                if (edge == Edge.BUSY) {
                    val aspect = width.toFloat() / height
                    return if (aspect in SQUARE_MIN_ASPECT..SQUARE_MAX_ASPECT) {
                        LogoLayout(true, Box(0, 0, width, height), WHITE, 0f, null)
                    } else {
                        LogoLayout(false, Box(0, 0, width, height), ring?.first ?: WHITE, LOGO_MARGIN, null)
                    }
                }
                val badgeColor = ring?.takeIf { it.second >= BADGE_SHARE }?.first
                val fill = when {
                    badgeColor != null -> badgeColor
                    edge == Edge.SOLID -> background
                    isLight(pixels, width, outside, box) -> DARK_BACKGROUND
                    else -> WHITE
                }
                // A badge on a background of another color: only the badge goes on the square.
                val clear = if (edge == Edge.SOLID && distance2(fill, background) > SAME_FILL) outside else null
                return LogoLayout(false, box, fill, if (badgeColor != null) BADGE_MARGIN else LOGO_MARGIN, clear)
            }

            private fun borderIndices(width: Int, height: Int): IntArray {
                val indices = ArrayList<Int>(2 * (width + height))
                for (x in 0 until width) {
                    indices += x
                    if (height > 1) indices += (height - 1) * width + x
                }
                for (y in 1 until height - 1) {
                    indices += y * width
                    if (width > 1) indices += y * width + width - 1
                }
                return indices.toIntArray()
            }

            private fun medianColor(pixels: IntArray, indices: IntArray): Int {
                fun median(channel: (Int) -> Int): Int {
                    val values = IntArray(indices.size) { channel(pixels[indices[it]]) }
                    values.sort()
                    return values[values.size / 2]
                }
                return rgb(median { red(it) }, median { green(it) }, median { blue(it) })
            }

            /** Marks the background reachable from the image's edges, so enclosed areas stay. */
            private fun floodFill(pixels: IntArray, width: Int, seeds: IntArray, background: Int, outside: BooleanArray) {
                val stack = IntArray(pixels.size)
                var size = 0
                for (i in seeds) {
                    if (!outside[i] && distance2(pixels[i], background) <= SAME_BACKGROUND) {
                        outside[i] = true
                        stack[size++] = i
                    }
                }
                while (size > 0) {
                    val i = stack[--size]
                    val x = i % width
                    for (direction in 0 until 4) {
                        val j = when (direction) {
                            0 -> if (x > 0) i - 1 else -1
                            1 -> if (x < width - 1) i + 1 else -1
                            2 -> i - width
                            else -> i + width
                        }
                        if (j < 0 || j >= outside.size || outside[j]) continue
                        if (distance2(pixels[j], background) > SAME_BACKGROUND) continue
                        outside[j] = true
                        stack[size++] = j
                    }
                }
            }

            /** The bounds of the logo itself: rows and columns with more than a speck of it. */
            private fun contentBox(outside: BooleanArray, width: Int, height: Int): Box? {
                val minInRow = maxOf(1, (width * 0.002f).roundToInt())
                val minInColumn = maxOf(1, (height * 0.002f).roundToInt())
                val inColumn = IntArray(width)
                var top = -1
                var bottom = -1
                for (y in 0 until height) {
                    var inRow = 0
                    for (x in 0 until width) {
                        if (!outside[y * width + x]) {
                            inRow++
                            inColumn[x]++
                        }
                    }
                    if (inRow >= minInRow) {
                        if (top < 0) top = y
                        bottom = y
                    }
                }
                val left = inColumn.indexOfFirst { it >= minInColumn }
                val right = inColumn.indexOfLast { it >= minInColumn }
                if (top < 0 || left < 0) return null
                return Box(left, top, right + 1, bottom + 1)
            }

            /**
             * The most common color on a line just inside [box], and the share of that line it
             * covers: close to 1 when the logo is a solid shape such as a tile or a circle.
             */
            private fun ringColor(pixels: IntArray, width: Int, outside: BooleanArray, box: Box): Pair<Int, Float>? {
                val inset = maxOf(1, (minOf(box.width, box.height) * RING_INSET).roundToInt())
                val stride = maxOf(1, (maxOf(box.width, box.height) / 200f).roundToInt())
                val left = box.left + inset
                val right = box.right - 1 - inset
                val top = box.top + inset
                val bottom = box.bottom - 1 - inset
                if (left > right || top > bottom) return null
                val samples = ArrayList<Int>()
                for (x in left..right step stride) {
                    samples += top * width + x
                    samples += bottom * width + x
                }
                for (y in top..bottom step stride) {
                    samples += y * width + left
                    samples += y * width + right
                }
                val buckets = HashMap<Int, LongArray>()
                for (i in samples) {
                    val color = pixels[i]
                    if (outside[i] || alpha(color) < 200) continue
                    val key = (red(color) shr 4 shl 8) or (green(color) shr 4 shl 4) or (blue(color) shr 4)
                    val sum = buckets.getOrPut(key) { LongArray(4) }
                    sum[0] += red(color).toLong()
                    sum[1] += green(color).toLong()
                    sum[2] += blue(color).toLong()
                    sum[3]++
                }
                val best = buckets.values.maxByOrNull { it[3] } ?: return null
                val fill = rgb((best[0] / best[3]).toInt(), (best[1] / best[3]).toInt(), (best[2] / best[3]).toInt())
                val matching = samples.count { !outside[it] && alpha(pixels[it]) >= 200 && distance2(pixels[it], fill) <= SAME_FILL }
                return fill to matching.toFloat() / samples.size
            }

            /** Whether the visible part of the logo is mostly white (it needs a dark background). */
            private fun isLight(pixels: IntArray, width: Int, outside: BooleanArray, box: Box): Boolean {
                var total = 0f
                var count = 0
                for (y in box.top until box.bottom step 2) {
                    for (x in box.left until box.right step 2) {
                        val i = y * width + x
                        val color = pixels[i]
                        if (outside[i] || alpha(color) < 128) continue
                        total += (0.299f * red(color) + 0.587f * green(color) + 0.114f * blue(color)) / 255f
                        count++
                    }
                }
                return count > 0 && total / count > LIGHT_LOGO_LUMINANCE
            }

            private const val WHITE = -0x1

            private fun alpha(color: Int) = (color ushr 24) and 0xFF
            private fun red(color: Int) = (color shr 16) and 0xFF
            private fun green(color: Int) = (color shr 8) and 0xFF
            private fun blue(color: Int) = color and 0xFF
            private fun rgb(red: Int, green: Int, blue: Int) = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

            private fun distance2(a: Int, b: Int): Int {
                val dr = red(a) - red(b)
                val dg = green(a) - green(b)
                val db = blue(a) - blue(b)
                return dr * dr + dg * dg + db * db
            }
        }
    }
}
