package io.github.eyalshimshi.shidur.playback

import android.os.SystemClock
import androidx.media3.common.Player
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pauses playback after a delay, optionally fading the volume out over the last 30 seconds. */
class SleepTimer(
    private val scope: CoroutineScope,
    private val playerProvider: () -> Player,
    private val onChanged: () -> Unit,
) {
    private var job: Job? = null
    private var restoreVolume: Float? = null
    private var fadeOut: Boolean = true

    var endElapsedRealtime: Long? = null
        private set

    fun start(durationMs: Long, fadeOut: Boolean) {
        cancel(notify = false)
        if (durationMs <= 0) {
            onChanged()
            return
        }
        this.fadeOut = fadeOut
        endElapsedRealtime = SystemClock.elapsedRealtime() + durationMs
        onChanged()
        job = scope.launch {
            val fadeMs = if (fadeOut) min(FADE_MS, durationMs) else 0L
            delay(durationMs - fadeMs)
            val player = playerProvider()
            if (fadeMs > 0) {
                val startVolume = player.volume
                restoreVolume = startVolume
                for (step in 1..FADE_STEPS) {
                    delay(fadeMs / FADE_STEPS)
                    player.volume = startVolume * (1f - step.toFloat() / FADE_STEPS)
                }
            }
            player.pause()
            restoreVolume?.let { player.volume = it }
            restoreVolume = null
            endElapsedRealtime = null
            job = null
            onChanged()
        }
    }

    fun extend(byMs: Long) {
        val end = endElapsedRealtime ?: return
        start(end - SystemClock.elapsedRealtime() + byMs, fadeOut)
    }

    fun cancel(notify: Boolean = true) {
        job?.cancel()
        job = null
        restoreVolume?.let { playerProvider().volume = it }
        restoreVolume = null
        if (endElapsedRealtime != null) {
            endElapsedRealtime = null
            if (notify) onChanged()
        }
    }

    private companion object {
        const val FADE_MS = 30_000L
        const val FADE_STEPS = 30
    }
}
