package io.github.eyalshimshi.shidur.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val connected: Boolean = false,
    val stationId: String? = null,
    val nowPlaying: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val hasError: Boolean = false,
    val queueIds: List<String> = emptyList(),
    /** SystemClock.elapsedRealtime() when the sleep timer fires, or null. */
    val sleepTimerEnd: Long? = null,
    val isCasting: Boolean = false,
)

/** The UI's connection to [PlaybackService], exposed as a [StateFlow]. Use from the main thread. */
class PlaybackConnection(context: Context) {

    private val appContext = context.applicationContext
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pendingActions = mutableListOf<(MediaController) -> Unit>()
    private var visibleScreens = 0

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun onScreenStarted() {
        visibleScreens++
        connect()
    }

    fun onScreenStopped() {
        visibleScreens = (visibleScreens - 1).coerceAtLeast(0)
        releaseIfUnused()
    }

    /**
     * Disconnects when no screen is visible. Media3 can only stop the playback service once every
     * controller is released, e.g. after the widget used this connection in the background.
     */
    fun releaseIfUnused() {
        if (visibleScreens == 0) release()
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull()
                if (connected == null) {
                    controllerFuture = null
                    return@addListener
                }
                controller = connected
                connected.addListener(playerListener)
                publish()
                val actions = pendingActions.toList()
                pendingActions.clear()
                actions.forEach { it(connected) }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        pendingActions.clear()
        _state.value = PlayerUiState()
    }

    fun play(stationId: String, queue: List<String>) = withController { c ->
        val ids = if (stationId in queue) queue.distinct() else listOf(stationId)
        c.setMediaItems(ids.map(MediaItemFactory::request), ids.indexOf(stationId), C.TIME_UNSET)
        c.prepare()
        c.play()
    }

    /** Loads a station without starting playback (used for shared links). */
    fun load(stationId: String) = withController { c ->
        c.setMediaItems(listOf(MediaItemFactory.request(stationId)), 0, C.TIME_UNSET)
        c.prepare()
    }

    fun togglePlayPause() = withController { c ->
        when {
            c.playerError != null -> c.sendCustomCommand(ShidurCommands.command(ShidurCommands.RETRY), Bundle.EMPTY)
            c.playWhenReady -> c.pause()
            else -> {
                if (c.playbackState == Player.STATE_IDLE) c.prepare()
                c.play()
            }
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPreviousMediaItem() }

    fun retry() = withController {
        it.sendCustomCommand(ShidurCommands.command(ShidurCommands.RETRY), Bundle.EMPTY)
    }

    fun setSleepTimer(durationMs: Long, fadeOut: Boolean) = withController {
        it.sendCustomCommand(
            ShidurCommands.command(ShidurCommands.SET_SLEEP_TIMER),
            bundleOf(ShidurCommands.ARG_DURATION_MS to durationMs, ShidurCommands.ARG_FADE_OUT to fadeOut),
        )
    }

    fun cancelSleepTimer() = withController {
        it.sendCustomCommand(ShidurCommands.command(ShidurCommands.CANCEL_SLEEP_TIMER), Bundle.EMPTY)
    }

    fun stop() = withController { c ->
        c.stop()
        c.clearMediaItems()
    }

    private fun withController(action: (MediaController) -> Unit) {
        val current = controller
        if (current != null) {
            action(current)
        } else {
            pendingActions += action
            connect()
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            publish()
        }

        override fun onDisconnected(controller: MediaController) {
            this@PlaybackConnection.controller = null
            controllerFuture = null
            _state.value = PlayerUiState()
        }
    }

    private fun publish() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val sleepEnd = c.sessionExtras.getLong(ShidurCommands.EXTRA_SLEEP_END_ELAPSED, 0L)
        _state.value = PlayerUiState(
            connected = true,
            stationId = item?.mediaId,
            nowPlaying = item?.mediaMetadata?.extras?.getString(ShidurCommands.EXTRA_NOW_PLAYING),
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            hasError = c.playerError != null,
            queueIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId },
            sleepTimerEnd = sleepEnd.takeIf { it > 0L },
            isCasting = c.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE,
        )
    }
}
