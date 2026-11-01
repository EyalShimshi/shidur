package io.github.eyalshimshi.shidur.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlin.math.min

/**
 * Retries transient network errors with exponential backoff (1, 2, 4, 8 s), but fails fast on
 * HTTP 4xx so the service can fall back to the station's next stream URL.
 *
 * While a station is starting, errors surface after [startupRetries] retries so a dead address
 * is abandoned in seconds instead of half a minute; once audio plays, it is patient again.
 */
@OptIn(UnstableApi::class)
class RadioLoadErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {

    @Volatile
    var startingUp: Boolean = true

    @Volatile
    var startupRetries: Int = 1

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val error = loadErrorInfo.exception
        if (error is HttpDataSource.InvalidResponseCodeException && error.responseCode in 400..499) {
            return C.TIME_UNSET
        }
        if (super.getRetryDelayMsFor(loadErrorInfo) == C.TIME_UNSET) return C.TIME_UNSET
        if (startingUp) return STARTUP_RETRY_DELAY_MS
        val exponent = (loadErrorInfo.errorCount - 1).coerceIn(0, 3)
        return min(1_000L shl exponent, 8_000L)
    }

    override fun getMinimumLoadableRetryCount(dataType: Int): Int = if (startingUp) startupRetries else 4

    private companion object {
        const val STARTUP_RETRY_DELAY_MS = 500L
    }
}
