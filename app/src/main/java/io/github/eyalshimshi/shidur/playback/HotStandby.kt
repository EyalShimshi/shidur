package io.github.eyalshimshi.shidur.playback

import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import io.github.eyalshimshi.shidur.data.USER_AGENT
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource

/**
 * Instant start (opt-in): keeps one station's stream open in the background, so playing that
 * station needs no new connection and starts from audio that is already downloaded.
 *
 * Runs on Wi-Fi and mobile data alike once the user turns it on (Settings states the data cost),
 * for at most [MAX_STANDBY_MS]; the app also stops it when it leaves the screen or another
 * station plays. The stream is read into a small ring buffer with its ICY metadata removed. When
 * the player opens the same station, [claim] hands the running connection to the player, which
 * starts a couple of seconds behind live.
 */
@OptIn(UnstableApi::class)
class HotStandby(private val client: OkHttpClient) {
    /** Song titles from a stream the player took over; called on the main thread. */
    @Volatile
    var onTitle: ((stationId: String, title: String) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stop() }
    private var current: Session? = null

    /** The station whose start was served by a standby stream, until [consumeInstantStart]. */
    @Volatile
    private var servedStationId: String? = null

    /** Starts keeping [stationId]'s stream ready, replacing any other standby stream. */
    @Synchronized
    fun start(stationId: String, url: String, matchingUrls: Set<String>, icyCharset: String?) {
        current?.let { session ->
            if (session.stationId == stationId && !session.isFinished()) return
            session.release()
        }
        val session = Session(stationId, url, matchingUrls + url, icyCharset)
        current = session
        session.start()
        mainHandler.removeCallbacks(timeout)
        mainHandler.postDelayed(timeout, MAX_STANDBY_MS)
    }

    /** Closes the standby stream unless the player already took it over. */
    @Synchronized
    fun stop() {
        mainHandler.removeCallbacks(timeout)
        current?.release()
        current = null
    }

    /** Closes the standby stream if it belongs to another station. */
    @Synchronized
    fun stopUnlessFor(stationId: String) {
        if (current != null && current?.stationId != stationId) stop()
    }

    /** True once after a start of [stationId] that played audio from the standby stream. */
    fun consumeInstantStart(stationId: String): Boolean {
        if (servedStationId != stationId) return false
        servedStationId = null
        return true
    }

    /**
     * Hands the standby stream over when the request is for its station ([key], see [stationKey])
     * or its address ([uri]). Each stream is handed over once.
     */
    internal fun claim(uri: Uri, key: String?): DataSource? {
        val session = synchronized(this) {
            val candidate = current ?: return null
            val forStation = key != null && key == stationKey(candidate.stationId)
            if (!(forStation || candidate.matches(uri.toString())) || candidate.isFinished()) return null
            current = null
            candidate
        }
        mainHandler.removeCallbacks(timeout)
        session.claimed = true
        session.latestTitle?.let { postTitle(session.stationId, it) }
        return StandbySource(session)
    }

    private fun postTitle(stationId: String, title: String) {
        mainHandler.post { onTitle?.invoke(stationId, title) }
    }

    /** One background connection to a station. */
    private inner class Session(
        val stationId: String,
        private val url: String,
        initialUrls: Set<String>,
        private val icyCharset: String?,
    ) {
        val ring = AudioRing(RING_BYTES)
        private val urls: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { addAll(initialUrls) }
        private val answered = CountDownLatch(1)

        @Volatile
        private var call: Call? = null

        @Volatile
        private var released = false

        @Volatile
        private var failure: IOException? = null

        @Volatile
        var headers: Map<String, List<String>> = emptyMap()
            private set

        @Volatile
        var bytesPerSecond = DEFAULT_BYTES_PER_SECOND
            private set

        @Volatile
        var latestTitle: String? = null
            private set

        @Volatile
        var claimed = false

        fun start() {
            Thread(::readStream, "shidur-instant-start").apply { isDaemon = true }.start()
        }

        fun matches(uri: String): Boolean = uri in urls

        fun isFinished(): Boolean = ring.isFinished()

        fun release() {
            released = true
            call?.cancel()
            ring.finish(null)
        }

        /** Waits for the server's answer; throws if the stream could not be opened. */
        fun awaitAnswer() {
            val ready = try {
                answered.await(ANSWER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException()
            }
            if (!ready) throw SocketTimeoutException("The station did not answer")
            failure?.let { throw it }
        }

        private fun readStream() {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Icy-MetaData", "1")
                    .header("User-Agent", USER_AGENT)
                    .build()
                val newCall = client.newCall(request)
                call = newCall
                if (released) return
                newCall.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty response")
                    urls += response.request.url.toString()
                    headers = playerHeaders(response)
                    response.header("icy-br")?.substringBefore(',')?.trim()?.toIntOrNull()?.let { kbps ->
                        if (kbps in 8..512) bytesPerSecond = kbps * 125
                    }
                    answered.countDown()
                    IcyDemuxer(response.header("icy-metaint")?.toIntOrNull()).pump(
                        source = body.source(),
                        isCancelled = { released },
                        onAudio = ring::write,
                        onMetadata = ::onMetadata,
                    )
                }
                ring.finish(null)
            } catch (e: IOException) {
                failure = e
                ring.finish(e)
            } catch (e: RuntimeException) {
                val wrapped = IOException(e)
                failure = wrapped
                ring.finish(wrapped)
            } finally {
                answered.countDown()
            }
        }

        private fun onMetadata(raw: ByteArray) {
            val title = IcyTitleDecoder.decodeStreamTitle(raw, icyCharset) ?: return
            if (title == latestTitle) return
            latestTitle = title
            if (claimed) postTitle(stationId, title)
        }
    }

    /** What the player reads after taking over a standby stream. */
    private inner class StandbySource(private val session: Session) : BaseDataSource(/* isNetwork= */ true) {
        private var uri: Uri? = null
        private var opened = false
        private var servedAudio = false

        override fun open(dataSpec: DataSpec): Long {
            uri = dataSpec.uri
            transferInitializing(dataSpec)
            session.awaitAnswer()
            session.ring.startReadingBehindLive(BACKLOG_SECONDS * session.bytesPerSecond)
            opened = true
            transferStarted(dataSpec)
            return C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val read = session.ring.read(buffer, offset, length, READ_TIMEOUT_MS)
            if (read < 0) return C.RESULT_END_OF_INPUT
            if (!servedAudio) {
                servedAudio = true
                servedStationId = session.stationId
            }
            bytesTransferred(read)
            return read
        }

        override fun getUri(): Uri? = uri

        override fun getResponseHeaders(): Map<String, List<String>> = session.headers

        override fun close() {
            uri = null
            if (opened) {
                opened = false
                transferEnded()
            }
            session.release()
        }
    }

    companion object {
        /** Longest time a standby stream stays open without being played. */
        const val MAX_STANDBY_MS = 2 * 60_000L

        /** About 30 seconds of 128 kbps audio. */
        private const val RING_BYTES = 512 * 1024

        /** Playback starts this far behind live, so the player's start buffer is already full. */
        private const val BACKLOG_SECONDS = 2
        private const val DEFAULT_BYTES_PER_SECOND = 16_000
        private const val ANSWER_TIMEOUT_MS = 8_000L
        private const val READ_TIMEOUT_MS = 20_000L

        /** Whether [url] is a plain audio stream (HLS and playlists can't be held open). */
        fun canHold(url: String, format: String?): Boolean {
            val path = url.substringBefore('?').lowercase(Locale.ROOT)
            return format != "hls" && !path.endsWith(".m3u8") && !path.endsWith(".m3u") && !path.endsWith(".pls")
        }

        /** The key that marks a station's plain-stream media items (their custom cache key). */
        fun stationKey(stationId: String): String = "shidur-station:$stationId"

        /** The response headers as the player should see them: ICY metadata is already removed. */
        private fun playerHeaders(response: Response): Map<String, List<String>> {
            val headers = TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER)
            for ((name, values) in response.headers.toMultimap()) {
                if (!name.equals("icy-metaint", ignoreCase = true)) headers[name] = values
            }
            return headers
        }
    }
}

/**
 * The player's network data source: takes over the Instant start stream when it is for the
 * requested address, and otherwise opens a normal connection with [upstream].
 */
@OptIn(UnstableApi::class)
class StandbyDataSourceFactory(
    private val upstream: DataSource.Factory,
    private val standby: HotStandby,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = StandbyAwareDataSource(upstream.createDataSource(), standby)
}

@OptIn(UnstableApi::class)
private class StandbyAwareDataSource(
    private val upstream: DataSource,
    private val standby: HotStandby,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        val claimed = if (dataSpec.position == 0L) standby.claim(dataSpec.uri, dataSpec.key) else null
        if (claimed != null) {
            listeners.forEach(claimed::addTransferListener)
            active = claimed
            try {
                return claimed.open(dataSpec)
            } catch (e: IOException) {
                runCatching { claimed.close() }
                active = null
                // A cancelled load must stop; otherwise a failed standby stream just means
                // connecting normally, rather than an error that would skip to another address.
                if (Thread.currentThread().isInterrupted) throw e
            }
        }
        active = upstream
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(active) { "read() before open()" }.read(buffer, offset, length)

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = active
        active = null
        source?.close()
    }
}

/** The newest bytes of a stream in a fixed-size buffer: one thread writes, another reads. */
class AudioRing(capacity: Int) {
    private val buffer = ByteArray(capacity)
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var writePosition = 0L
    private var readPosition = 0L
    private var finished = false
    private var failure: IOException? = null

    fun write(source: ByteArray, offset: Int, length: Int) {
        lock.withLock {
            if (finished) return
            var from = offset
            var remaining = length
            while (remaining > 0) {
                val at = (writePosition % buffer.size).toInt()
                val count = minOf(remaining, buffer.size - at)
                System.arraycopy(source, from, buffer, at, count)
                writePosition += count
                from += count
                remaining -= count
            }
            changed.signalAll()
        }
    }

    /** Ends the stream; readers get the rest of the data, then [error] or the end of input. */
    fun finish(error: IOException?) {
        lock.withLock {
            if (finished) return
            finished = true
            failure = error
            changed.signalAll()
        }
    }

    fun isFinished(): Boolean = lock.withLock { finished }

    /** Moves the reader to [bytes] behind the newest data, or to the oldest data still kept. */
    fun startReadingBehindLive(bytes: Int) {
        lock.withLock { readPosition = maxOf(oldestPosition(), writePosition - bytes) }
    }

    /**
     * Copies up to [length] bytes into [target], waiting up to [timeoutMs] for data. Returns the
     * number of bytes, or -1 when the stream ended. A reader that fell behind skips ahead.
     */
    fun read(target: ByteArray, offset: Int, length: Int, timeoutMs: Long): Int {
        lock.withLock {
            var waitNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMs)
            while (readPosition >= writePosition) {
                if (finished) {
                    failure?.let { throw it }
                    return -1
                }
                if (waitNanos <= 0L) throw SocketTimeoutException("No audio for $timeoutMs ms")
                waitNanos = try {
                    changed.awaitNanos(waitNanos)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException()
                }
            }
            readPosition = maxOf(readPosition, oldestPosition())
            val at = (readPosition % buffer.size).toInt()
            val count = minOf(length.toLong(), writePosition - readPosition, (buffer.size - at).toLong()).toInt()
            System.arraycopy(buffer, at, target, offset, count)
            readPosition += count
            return count
        }
    }

    private fun oldestPosition(): Long = maxOf(0L, writePosition - buffer.size)
}

/** Splits a SHOUTcast/Icecast stream into audio and its in-band metadata blocks. */
class IcyDemuxer(metadataInterval: Int?) {

    private val interval = metadataInterval?.takeIf { it > 0 }

    fun pump(
        source: BufferedSource,
        isCancelled: () -> Boolean,
        onAudio: (ByteArray, Int, Int) -> Unit,
        onMetadata: (ByteArray) -> Unit,
    ) {
        val chunk = ByteArray(CHUNK_BYTES)
        var untilMetadata = interval ?: Int.MAX_VALUE
        while (!isCancelled()) {
            if (interval != null && untilMetadata == 0) {
                val length = (source.readByte().toInt() and 0xFF) * 16
                if (length > 0) onMetadata(source.readByteArray(length.toLong()))
                untilMetadata = interval
                continue
            }
            val read = source.read(chunk, 0, minOf(chunk.size, untilMetadata))
            if (read == -1) return
            onAudio(chunk, 0, read)
            if (interval != null) untilMetadata -= read
        }
    }

    private companion object {
        const val CHUNK_BYTES = 8 * 1024
    }
}
