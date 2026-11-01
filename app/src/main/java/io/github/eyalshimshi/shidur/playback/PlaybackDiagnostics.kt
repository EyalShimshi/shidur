package io.github.eyalshimshi.shidur.playback

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One measured "tap → first audio" start. */
data class StartRecord(
    val stationId: String,
    val millis: Long,
    val host: String,
    /** The station was warmed up before it was played. */
    val warmed: Boolean,
    /** Playback skipped a redirect hop thanks to the warm-up. */
    val skippedRedirect: Boolean,
    /** How many stream addresses were tried before audio started. */
    val attempts: Int,
    /** Audio came from the stream that Instant start kept ready. */
    val instant: Boolean = false,
)

/** Measures how long stations take to start playing, from the request until audio is heard. */
class PlaybackDiagnostics(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val requestedAt = ConcurrentHashMap<String, Long>()
    private val _starts = MutableStateFlow<List<StartRecord>>(emptyList())

    /** Most recent first. */
    val starts: StateFlow<List<StartRecord>> = _starts.asStateFlow()

    /** Called when the user asks for a station; starts the clock. */
    fun markRequested(stationId: String) {
        requestedAt[stationId] = clock()
    }

    /** Starts the clock unless a recent request already did (e.g. playback started from a car). */
    fun markIfAbsent(stationId: String) {
        val now = clock()
        val previous = requestedAt[stationId]
        if (previous == null || now - previous > STALE_MS) requestedAt[stationId] = now
    }

    /** Records a start when audio begins; returns null if no request was pending. */
    fun onAudioStarted(
        stationId: String,
        host: String,
        warmed: Boolean,
        skippedRedirect: Boolean,
        attempts: Int,
        instant: Boolean = false,
    ): StartRecord? {
        val requested = requestedAt.remove(stationId) ?: return null
        val elapsed = clock() - requested
        if (elapsed > STALE_MS) return null
        val record = StartRecord(stationId, elapsed, host, warmed, skippedRedirect, attempts, instant)
        _starts.update { (listOf(record) + it).take(MAX_RECORDS) }
        return record
    }

    fun clear() {
        requestedAt.clear()
        _starts.value = emptyList()
    }

    private companion object {
        const val STALE_MS = 60_000L
        const val MAX_RECORDS = 20
    }
}
