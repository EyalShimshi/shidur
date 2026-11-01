package io.github.eyalshimshi.shidur.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import io.github.eyalshimshi.shidur.AppContainer
import io.github.eyalshimshi.shidur.MainActivity
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.ShidurApplication
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.USER_AGENT
import io.github.eyalshimshi.shidur.data.isHebrewUi
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.widget.ShidurWidget
import io.github.eyalshimshi.shidur.widget.WidgetPlaybackState
import io.github.eyalshimshi.shidur.widget.WidgetStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    internal val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    internal val container: AppContainer
        get() = (application as ShidurApplication).container

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: Player
    private lateinit var session: MediaLibrarySession
    private lateinit var sleepTimer: SleepTimer
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var audioManager: AudioManager
    private lateinit var loadErrorPolicy: RadioLoadErrorHandlingPolicy

    private var stationsById: Map<String, Station> = emptyMap()
    private var favoriteIds: List<String> = emptyList()
    private var settings = AppSettings()
    private val workingStreamIndex = mutableMapOf<String, Int>()
    private var lastStationId: String? = null
    private var nowPlayingTitle: String? = null
    private var recentJob: Job? = null
    private var stopAfterPauseJob: Job? = null
    private var widgetJob: Job? = null
    private var audioCallbackRegisteredAt = 0L
    private var currentNetwork: Network? = null

    /** Stream addresses tried for the station that is currently starting. */
    private var startAttempts = 0

    /** The station the wake-up alarm is playing; while set, audio uses the alarm volume. */
    private var alarmStationId: String? = null
    private var alarmGentle = false
    private var alarmAudioStarted = false
    private var alarmBackupJob: Job? = null
    private var alarmFadeJob: Job? = null
    private var alarmToneJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(ConnectivityManager::class.java)
        audioManager = getSystemService(AudioManager::class.java)

        exoPlayer = buildExoPlayer()
        player = buildSessionPlayer(exoPlayer)
        player.repeatMode = Player.REPEAT_MODE_ALL
        sleepTimer = SleepTimer(serviceScope, { player }) { publishSessionExtras() }

        session = MediaLibrarySession.Builder(this, player, LibrarySessionCallback(this))
            .setSessionActivity(sessionActivity())
            .build()
        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_stat_shidur)
        setMediaNotificationProvider(notificationProvider)

        exoPlayer.addListener(localPlayerListener)
        exoPlayer.addAnalyticsListener(analyticsListener)
        player.addListener(sessionPlayerListener)
        container.hotStandby.onTitle = ::onStandbyTitle
        observeData()

        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        audioCallbackRegisteredAt = SystemClock.elapsedRealtime()
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == AlarmScheduler.ACTION_START) {
            intent.getStringExtra(AlarmScheduler.EXTRA_STATION)?.let { stationId ->
                startAlarm(stationId, intent.getBooleanExtra(AlarmScheduler.EXTRA_GENTLE, true))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        container.hotStandby.onTitle = null
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        sleepTimer.cancel(notify = false)
        serviceScope.cancel()
        session.release()
        player.release()
        if (player !== exoPlayer) runCatching { exoPlayer.release() }
        super.onDestroy()
    }

    // region Player setup

    private fun buildExoPlayer(): ExoPlayer {
        // Shares the app's OkHttp client, so DNS answers, TLS sessions and connections opened by
        // warm-ups are reused by playback. The standby factory hands over the stream that Instant
        // start kept open, when there is one for the requested address.
        val httpFactory = OkHttpDataSource.Factory(container.httpClient).setUserAgent(USER_AGENT)
        val networkFactory = StandbyDataSourceFactory(httpFactory, container.hotStandby)
        loadErrorPolicy = RadioLoadErrorHandlingPolicy()
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(this, networkFactory))
            .setLoadErrorHandlingPolicy(loadErrorPolicy)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 30_000, START_BUFFER_MS, REBUFFER_MS)
            .build()
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(MEDIA_AUDIO, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
    }

    /** Wraps the local player in Media3's CastPlayer, which moves playback to a Cast device and back. */
    private fun buildSessionPlayer(local: ExoPlayer): Player = try {
        CastPlayer.Builder(this).setLocalPlayer(local).build()
    } catch (t: Throwable) {
        Log.w(TAG, "Cast playback unavailable; using local playback only", t)
        local
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER, true)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun observeData() {
        serviceScope.launch {
            container.catalogRepository.stations.collect { list ->
                stationsById = list.associateBy { it.id }
                updateWidget()
            }
        }
        serviceScope.launch {
            container.libraryRepository.favoriteIds.collect { ids ->
                favoriteIds = ids
                updateMediaButtons()
                updateWidget()
            }
        }
        serviceScope.launch {
            container.settingsRepository.settings.collect { settings = it }
        }
    }

    // endregion

    // region Used by LibrarySessionCallback

    internal fun hebrew(): Boolean = isHebrewUi(this)

    internal suspend fun awaitStations(): List<Station> = container.catalogRepository.awaitStations()

    internal fun playableItem(station: Station, mediaId: String = station.id): MediaItem {
        val streamIndex = workingStreamIndex[station.id] ?: container.streamSelector.preferredIndex(station)
        return buildItem(station, streamIndex, mediaId)
    }

    /**
     * Gives a warm-up that is still running for [stationId] (started when the user touched it) a
     * moment to finish, so playback can skip its redirect hop.
     */
    internal suspend fun awaitWarmUp(stationId: String) {
        val station = awaitStations().firstOrNull { it.id == stationId } ?: return
        val streamIndex = workingStreamIndex[station.id] ?: container.streamSelector.preferredIndex(station)
        val url = station.streams.getOrNull(streamIndex)?.url ?: return
        container.streamWarmer.awaitInFlight(url, WARM_UP_WAIT_MS)
    }

    private fun buildItem(
        station: Station,
        streamIndex: Int,
        mediaId: String,
        useWarmUp: Boolean = true,
        nowPlaying: String? = null,
    ): MediaItem {
        val index = streamIndex.coerceIn(0, station.streams.lastIndex)
        val resolved = if (useWarmUp) container.streamWarmer.resolvedUrl(station.streams[index].url) else null
        return MediaItemFactory.playable(this, station, hebrew(), index, nowPlaying, mediaId, resolved)
    }

    internal fun mediaButtonPreferences(): List<CommandButton> {
        val stationId = player.currentMediaItem?.mediaId
        val isFavorite = stationId != null && stationId in favoriteIds
        val favoriteButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(getString(if (isFavorite) R.string.favorite_remove else R.string.favorite_add))
            .setSessionCommand(ShidurCommands.command(ShidurCommands.TOGGLE_FAVORITE))
            .build()
        return listOf(favoriteButton)
    }

    internal fun toggleCurrentFavorite() {
        val stationId = player.currentMediaItem?.mediaId ?: return
        if (stationId !in stationsById) return
        serviceScope.launch { container.libraryRepository.toggleFavorite(stationId) }
    }

    internal fun startSleepTimer(durationMs: Long, fadeOut: Boolean) {
        sleepTimer.start(durationMs, fadeOut)
    }

    internal fun cancelSleepTimer() {
        sleepTimer.cancel()
    }

    /** Restarts the current station from its preferred stream URL. */
    internal fun retryCurrentStation() {
        val current = player.currentMediaItem ?: return
        val station = stationsById[current.mediaId] ?: return
        workingStreamIndex.remove(station.id)
        startAttempts = 0
        val item = playableItem(station, current.mediaId)
        beginStartup(station, item.streamIndex())
        player.replaceMediaItem(player.currentMediaItemIndex, item)
        player.prepare()
        player.play()
    }

    // endregion

    // region Playback behavior

    private val localPlayerListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            handleMetadata(metadata)
        }
    }

    /** Fires when audio actually starts coming out of the speaker: the end of a "start". */
    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioPositionAdvancing(
            eventTime: AnalyticsListener.EventTime,
            playoutStartSystemTimeMs: Long,
        ) {
            onAudioStarted()
        }
    }

    private val sessionPlayerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val stationId = mediaItem?.mediaId
            // An Instant start stream for another station would only waste data now.
            stationId?.let { container.hotStandby.stopUnlessFor(it) }
            // Switching to another station after waking up ends the alarm.
            if (alarmStationId != null && stationId != alarmStationId && stationId != ALARM_TONE_ID) endAlarm()
            if (stationId != lastStationId) {
                lastStationId = stationId
                nowPlayingTitle = null
                startAttempts = 0
                val station = stationId?.let { stationsById[it] }
                if (station != null && mediaItem != null) {
                    beginStartup(station, mediaItem.streamIndex())
                    if (player.playWhenReady) container.diagnostics.markIfAbsent(station.id)
                }
                rememberLastPlayed()
                scheduleRecent()
                updateMediaButtons()
            }
            updateWidget()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            stopAfterPauseJob?.cancel()
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) endAlarm()
            if (!playWhenReady) {
                // Live radio: release the connection after a pause, and rejoin live on resume.
                stopAfterPauseJob = serviceScope.launch {
                    delay(STOP_AFTER_PAUSE_MS)
                    if (!player.playWhenReady && player.playbackState != Player.STATE_IDLE) {
                        player.stop()
                    }
                }
            } else if (player.playbackState != Player.STATE_READY) {
                // Resuming needs a new connection (the buffer is gone): measure it like a start.
                val item = player.currentMediaItem
                val station = item?.let { stationsById[it.mediaId] }
                if (item != null && station != null) {
                    beginStartup(station, item.streamIndex())
                    container.diagnostics.markIfAbsent(station.id)
                }
            }
            updateWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleRecent()
            updateWidget()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Playback error: ${error.errorCodeName}", error)
            handleStreamFailure()
        }
    }

    private fun handleMetadata(metadata: Metadata) {
        val current = player.currentMediaItem ?: return
        val station = stationsById[current.mediaId] ?: return
        for (i in 0 until metadata.length()) {
            val title = when (val entry = metadata.get(i)) {
                is IcyInfo -> IcyTitleDecoder.decodeStreamTitle(entry.rawMetadata, station.icyCharset)
                    ?: entry.title?.let { IcyTitleDecoder.clean(it) }
                is TextInformationFrame ->
                    if (entry.id == "TIT2") entry.values.firstOrNull()?.let { IcyTitleDecoder.clean(it) } else null
                else -> null
            } ?: continue
            applyStreamTitle(station, title)
        }
    }

    /** Titles from an Instant start stream, which carries its metadata outside the player. */
    private fun onStandbyTitle(stationId: String, title: String) {
        if (player.currentMediaItem?.mediaId != stationId) return
        val station = stationsById[stationId] ?: return
        applyStreamTitle(station, title)
    }

    private fun applyStreamTitle(station: Station, title: String) {
        // Some stations send their own name instead of a song.
        if (title.equals(station.nameHe, ignoreCase = true) || title.equals(station.nameEn, ignoreCase = true)) return
        updateNowPlaying(station, title)
    }

    private fun updateNowPlaying(station: Station, title: String) {
        if (title == nowPlayingTitle) return
        val current = player.currentMediaItem ?: return
        if (current.mediaId != station.id) return
        nowPlayingTitle = title
        // Same URI, new metadata: ExoPlayer updates the item without interrupting playback.
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            MediaItemFactory.withNowPlaying(this, current, station, hebrew(), title),
        )
        updateWidget()
    }

    /**
     * Recovers from a fatal error: first retries the station's own address if a pre-resolved one
     * failed, then falls back to the station's next stream address.
     */
    private fun handleStreamFailure() {
        val current = player.currentMediaItem ?: return
        val station = stationsById[current.mediaId] ?: return
        val extras = current.mediaMetadata.extras
        val streamIndex = current.streamIndex()
        current.localConfiguration?.uri?.host?.let { container.dns.invalidate(it) }
        startAttempts++
        if (extras?.getBoolean(ShidurCommands.EXTRA_SKIPPED_REDIRECT) == true) {
            station.streams.getOrNull(streamIndex)?.url?.let { container.streamWarmer.forget(it) }
            restartWith(station, streamIndex, useWarmUp = false)
            return
        }
        val next = streamIndex + 1
        if (next > station.streams.lastIndex) {
            workingStreamIndex.remove(station.id)
            // Every address failed: a wake-up alarm must still make a sound.
            if (alarmStationId != null) playAlarmTone()
            return
        }
        workingStreamIndex[station.id] = next
        restartWith(station, next, useWarmUp = true)
    }

    private fun restartWith(station: Station, streamIndex: Int, useWarmUp: Boolean) {
        val mediaId = player.currentMediaItem?.mediaId ?: station.id
        beginStartup(station, streamIndex)
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            buildItem(station, streamIndex, mediaId, useWarmUp),
        )
        player.prepare()
        player.play()
    }

    /** While a station starts, give up on a dead address quickly if another one is available. */
    private fun beginStartup(station: Station, streamIndex: Int) {
        loadErrorPolicy.startupRetries = if (streamIndex < station.streams.lastIndex) 0 else 2
        loadErrorPolicy.startingUp = true
    }

    private fun onAudioStarted() {
        loadErrorPolicy.startingUp = false
        // Audio is playing; a standby stream that wasn't handed over is no longer useful.
        container.hotStandby.stop()
        val item = player.currentMediaItem ?: return
        if (alarmStationId != null && item.mediaId != ALARM_TONE_ID && !alarmAudioStarted) {
            alarmAudioStarted = true
            alarmBackupJob?.cancel()
            if (alarmGentle) fadeInAlarm()
        }
        val station = stationsById[item.mediaId] ?: return
        val extras = item.mediaMetadata.extras
        container.streamSelector.rememberWorking(station, item.streamIndex())
        container.diagnostics.onAudioStarted(
            stationId = station.id,
            host = item.localConfiguration?.uri?.host.orEmpty(),
            warmed = extras?.getBoolean(ShidurCommands.EXTRA_WARMED) ?: false,
            skippedRedirect = extras?.getBoolean(ShidurCommands.EXTRA_SKIPPED_REDIRECT) ?: false,
            attempts = startAttempts + 1,
            instant = container.hotStandby.consumeInstantStart(station.id),
        )
        warmNeighbors()
    }

    /**
     * Warms the previous and next stations in the queue, and swaps the warmed addresses into the
     * queue, so skipping between stations starts faster.
     */
    private fun warmNeighbors() {
        val count = player.mediaItemCount
        if (count < 2) return
        val currentIndex = player.currentMediaItemIndex
        val neighbors = listOf((currentIndex + 1) % count, (currentIndex - 1 + count) % count)
            .distinct()
            .filter { it != currentIndex }
        for (position in neighbors) {
            val item = player.getMediaItemAt(position)
            val station = stationsById[item.mediaId] ?: continue
            val streamIndex = item.streamIndex()
            val url = station.streams.getOrNull(streamIndex)?.url ?: continue
            serviceScope.launch {
                val resolved = container.streamWarmer.resolve(url) ?: return@launch
                if (position >= player.mediaItemCount || position == player.currentMediaItemIndex) return@launch
                val queued = player.getMediaItemAt(position)
                if (queued.mediaId == station.id && queued.localConfiguration?.uri?.toString() != resolved) {
                    player.replaceMediaItem(position, buildItem(station, streamIndex, queued.mediaId))
                }
            }
        }
    }

    private fun MediaItem.streamIndex(): Int =
        mediaMetadata.extras?.getInt(ShidurCommands.EXTRA_STREAM_INDEX) ?: 0

    // region Wake-up alarm

    /**
     * Plays [stationId] for the wake-up alarm: on the alarm volume, with the favorites queued so
     * the user can switch station, quietly at first when [gentle].
     */
    private fun startAlarm(stationId: String, gentle: Boolean) {
        // No app screen may have connected yet; the session must be known to show the notification.
        if (!isSessionAdded(session)) addSession(session)
        enterAlarmMode(stationId, gentle)
        serviceScope.launch {
            val byId = awaitStations().associateBy { it.id }
            val station = byId[stationId]
            if (station == null) {
                playAlarmTone()
                return@launch
            }
            val favorites = container.libraryRepository.favoriteIds.first()
            val queue = (if (station.id in favorites) favorites else listOf(station.id)).mapNotNull { byId[it] }
            val items = queue.map { playableItem(it) }
            container.diagnostics.markRequested(station.id)
            player.setMediaItems(items, items.indexOfFirst { it.mediaId == station.id }.coerceAtLeast(0), C.TIME_UNSET)
            player.prepare()
            player.play()
            alarmBackupJob?.cancel()
            alarmBackupJob = serviceScope.launch {
                delay(ALARM_BACKUP_AFTER_MS)
                if (alarmStationId != null && !alarmAudioStarted) playAlarmTone()
            }
        }
    }

    private fun enterAlarmMode(stationId: String, gentle: Boolean) {
        alarmStationId = stationId
        alarmGentle = gentle
        alarmAudioStarted = false
        exoPlayer.setAudioAttributes(ALARM_AUDIO, true)
        exoPlayer.volume = if (gentle) ALARM_START_VOLUME else 1f
    }

    /** Back to normal listening: media volume, full loudness, the queue repeating again. */
    private fun endAlarm() {
        if (alarmStationId == null) return
        alarmStationId = null
        alarmBackupJob?.cancel()
        alarmFadeJob?.cancel()
        alarmToneJob?.cancel()
        exoPlayer.volume = 1f
        exoPlayer.setAudioAttributes(MEDIA_AUDIO, true)
        player.repeatMode = Player.REPEAT_MODE_ALL
    }

    /** Raises the volume over a minute, slowly at first (loudness grows roughly evenly). */
    private fun fadeInAlarm() {
        alarmFadeJob?.cancel()
        alarmFadeJob = serviceScope.launch {
            val start = exoPlayer.volume
            val steps = (ALARM_FADE_MS / ALARM_FADE_STEP_MS).toInt()
            for (step in 1..steps) {
                delay(ALARM_FADE_STEP_MS)
                val fraction = step.toFloat() / steps
                exoPlayer.volume = start + (1f - start) * fraction * fraction
            }
        }
    }

    /** The phone's own alarm sound, for when the station can't be reached. */
    private fun playAlarmTone() {
        alarmBackupJob?.cancel()
        alarmFadeJob?.cancel()
        exoPlayer.volume = 1f
        val metadata = MediaMetadata.Builder()
            .setTitle(getString(R.string.alarm_tone_title))
            .setArtist(getString(R.string.app_name))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .build()
        val tone = MediaItem.Builder()
            .setMediaId(ALARM_TONE_ID)
            .setUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setMediaMetadata(metadata)
            .build()
        player.setMediaItem(tone)
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.prepare()
        player.play()
        alarmToneJob?.cancel()
        alarmToneJob = serviceScope.launch {
            delay(ALARM_TONE_MAX_MS)
            if (player.currentMediaItem?.mediaId == ALARM_TONE_ID) player.stop()
            endAlarm()
        }
    }

    // endregion

    private fun scheduleRecent() {
        recentJob?.cancel()
        val stationId = player.currentMediaItem?.mediaId ?: return
        if (stationId == ALARM_TONE_ID) return
        recentJob = serviceScope.launch {
            delay(RECENT_AFTER_MS)
            if (player.isPlaying && player.currentMediaItem?.mediaId == stationId) {
                container.libraryRepository.recordRecent(stationId)
            }
        }
    }

    private fun rememberLastPlayed() {
        val stationId = player.currentMediaItem?.mediaId ?: return
        if (stationId == ALARM_TONE_ID) return
        val queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        serviceScope.launch { container.libraryRepository.setLastPlayed(stationId, queue) }
    }

    private fun updateMediaButtons() {
        if (::session.isInitialized) {
            session.setMediaButtonPreferences(mediaButtonPreferences())
        }
    }

    private fun publishSessionExtras() {
        if (!::session.isInitialized) return
        val extras = Bundle()
        sleepTimer.endElapsedRealtime?.let { extras.putLong(ShidurCommands.EXTRA_SLEEP_END_ELAPSED, it) }
        session.setSessionExtras(extras)
    }

    private fun updateWidget() {
        if (!::player.isInitialized) return
        val station = player.currentMediaItem?.let { stationsById[it.mediaId] }
        WidgetStateHolder.state.value = WidgetPlaybackState(
            stationId = station?.id,
            stationName = station?.name(hebrew()),
            nowPlaying = nowPlayingTitle,
            isPlaying = player.playWhenReady && player.playbackState != Player.STATE_IDLE,
        )
        widgetJob?.cancel()
        widgetJob = serviceScope.launch {
            delay(WIDGET_UPDATE_DEBOUNCE_MS)
            runCatching { ShidurWidget.refreshAll(this@PlaybackService) }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            serviceScope.launch {
                val previous = currentNetwork
                currentNetwork = network
                if (previous != null && previous != network) {
                    // New network: cached DNS answers and pooled connections belong to the old one.
                    container.dns.clear()
                    container.httpClient.connectionPool.evictAll()
                }
                if (player.playerError != null && player.playWhenReady) retryCurrentStation()
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // The first callback lists devices that were already connected; ignore it.
            if (SystemClock.elapsedRealtime() - audioCallbackRegisteredAt < 2_000) return
            if (!settings.resumeOnHeadphones) return
            if (addedDevices.none { it.type in WIRED_HEADPHONE_TYPES }) return
            if (player.isPlaying || player.mediaItemCount == 0) return
            val mode = audioManager.mode
            if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_IN_COMMUNICATION) return
            player.prepare()
            player.play()
        }
    }

    // endregion

    private companion object {
        const val TAG = "PlaybackService"
        const val STOP_AFTER_PAUSE_MS = 10_000L
        const val RECENT_AFTER_MS = 10_000L
        const val WIDGET_UPDATE_DEBOUNCE_MS = 500L

        /** Audio buffered before playback starts. Most radio servers send a burst on connect. */
        const val START_BUFFER_MS = 750
        const val REBUFFER_MS = 2_500

        /** How long a tap waits for a warm-up already in progress for that station. */
        const val WARM_UP_WAIT_MS = 250L
        val WIRED_HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )

        val MEDIA_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        /** The wake-up alarm plays on the alarm volume, which people keep up at night. */
        val ALARM_AUDIO: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_ALARM)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        /** Media id of the phone's alarm sound, played when the alarm's station can't be reached. */
        const val ALARM_TONE_ID = "shidur-alarm-tone"
        const val ALARM_START_VOLUME = 0.08f
        const val ALARM_FADE_MS = 60_000L
        const val ALARM_FADE_STEP_MS = 500L
        const val ALARM_BACKUP_AFTER_MS = 30_000L
        const val ALARM_TONE_MAX_MS = 10 * 60_000L
    }
}
