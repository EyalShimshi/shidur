package io.github.eyalshimshi.shidur.playback

import android.os.Bundle
import androidx.media3.session.SessionCommand

/** Custom media-session commands and extras shared by the service, the UI and the widget. */
object ShidurCommands {
    const val TOGGLE_FAVORITE = "io.github.eyalshimshi.shidur.TOGGLE_FAVORITE"
    const val SET_SLEEP_TIMER = "io.github.eyalshimshi.shidur.SET_SLEEP_TIMER"
    const val CANCEL_SLEEP_TIMER = "io.github.eyalshimshi.shidur.CANCEL_SLEEP_TIMER"
    const val RETRY = "io.github.eyalshimshi.shidur.RETRY"

    const val ARG_DURATION_MS = "duration_ms"
    const val ARG_FADE_OUT = "fade_out"

    /** Session extra: SystemClock.elapsedRealtime() at which the sleep timer fires. */
    const val EXTRA_SLEEP_END_ELAPSED = "sleep_end_elapsed"

    /** Media item metadata extras. */
    const val EXTRA_STREAM_INDEX = "stream_index"
    const val EXTRA_NOW_PLAYING = "now_playing"

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)
}
