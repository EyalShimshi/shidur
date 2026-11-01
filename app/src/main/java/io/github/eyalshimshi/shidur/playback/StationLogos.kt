package io.github.eyalshimshi.shidur.playback

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
        val logo = LogoNormalizer.normalize(bytes, OUTPUT_PX) ?: return null
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

    /** One file per station and logo address, so a changed address is downloaded again. */
    private fun key(station: Station): String? =
        station.logo?.let { "${station.id}-${it.hashCode().toUInt().toString(16)}" }

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

/** Turns a downloaded logo into square artwork. */
internal object LogoNormalizer {

    private const val PADDING_FRACTION = 0.12f
    private const val SQUARE_MIN_ASPECT = 0.8f
    private const val SQUARE_MAX_ASPECT = 1.25f
    private const val LIGHT_LOGO_LUMINANCE = 0.82f
    private const val DARK_BACKGROUND = 0xFF1C1B1F.toInt()

    /** The smaller side of the image in [bytes], in pixels; 0 when it isn't a readable image. */
    fun smallerSide(bytes: ByteArray): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) minOf(bounds.outWidth, bounds.outHeight) else 0
    }

    /**
     * Square artwork of [outputPx] pixels: a square icon with its own background fills it; any
     * other logo is fitted with a margin on a background it stands out on.
     */
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
            val output = Bitmap.createBitmap(outputPx, outputPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val opaque = hasOpaqueEdges(source)
            val aspect = source.width.toFloat() / source.height
            if (opaque && aspect >= SQUARE_MIN_ASPECT && aspect <= SQUARE_MAX_ASPECT) {
                val side = minOf(source.width, source.height)
                val left = (source.width - side) / 2
                val top = (source.height - side) / 2
                canvas.drawBitmap(source, Rect(left, top, left + side, top + side), Rect(0, 0, outputPx, outputPx), paint)
            } else {
                val background = when {
                    opaque -> source.getPixel(0, 0)
                    isLight(source) -> DARK_BACKGROUND
                    else -> Color.WHITE
                }
                canvas.drawColor(background)
                val box = outputPx * (1f - 2f * PADDING_FRACTION)
                val scale = minOf(box / source.width, box / source.height)
                val width = source.width * scale
                val height = source.height * scale
                val left = (outputPx - width) / 2f
                val top = (outputPx - height) / 2f
                canvas.drawBitmap(source, null, RectF(left, top, left + width, top + height), paint)
            }
            return output
        } finally {
            source.recycle()
        }
    }

    /** Whether the image's border is fully opaque, i.e. it brings its own background. */
    private fun hasOpaqueEdges(bitmap: Bitmap): Boolean {
        val right = bitmap.width - 1
        val bottom = bitmap.height - 1
        val points = listOf(
            0 to 0, right to 0, 0 to bottom, right to bottom,
            right / 2 to 0, right / 2 to bottom, 0 to bottom / 2, right to bottom / 2,
        )
        return points.all { (x, y) -> Color.alpha(bitmap.getPixel(x, y)) >= 250 }
    }

    /** Whether the visible part of a transparent logo is mostly white (it needs a dark background). */
    private fun isLight(bitmap: Bitmap): Boolean {
        var total = 0f
        var count = 0
        val steps = 16
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val pixel = bitmap.getPixel(i * (bitmap.width - 1) / (steps - 1), j * (bitmap.height - 1) / (steps - 1))
                if (Color.alpha(pixel) < 128) continue
                total += (0.299f * Color.red(pixel) + 0.587f * Color.green(pixel) + 0.114f * Color.blue(pixel)) / 255f
                count++
            }
        }
        return count > 0 && total / count > LIGHT_LOGO_LUMINANCE
    }
}
