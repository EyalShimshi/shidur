package io.github.eyalshimshi.shidur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eyalshimshi.shidur.CrashReporter
import io.github.eyalshimshi.shidur.ShidurApplication
import io.github.eyalshimshi.shidur.data.AlarmConfig
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.DataSaverMode
import io.github.eyalshimshi.shidur.data.ThemeMode
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.StationCollection
import io.github.eyalshimshi.shidur.playback.AlarmScheduler
import io.github.eyalshimshi.shidur.playback.HotStandby
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.playback.StartRecord
import io.github.eyalshimshi.shidur.playback.StreamProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class LibraryUiState(
    val loaded: Boolean = false,
    val stations: List<Station> = emptyList(),
    val stationsById: Map<String, Station> = emptyMap(),
    val collections: List<StationCollection> = emptyList(),
    val favorites: List<Station> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val recents: List<Station> = emptyList(),
    val catalogVersion: Long = 0,
)

class ShidurViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as ShidurApplication).container
    private val catalogRepository = container.catalogRepository
    private val libraryRepository = container.libraryRepository
    private val settingsRepository = container.settingsRepository

    val playback = container.playbackConnection

    private var instantStartJob: Job? = null

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val library: StateFlow<LibraryUiState> = combine(
        catalogRepository.catalog.filterNotNull(),
        catalogRepository.stations,
        libraryRepository.favoriteIds,
        libraryRepository.recentIds,
    ) { catalog, stations, favoriteIds, recentIds ->
        val byId = stations.associateBy { it.id }
        LibraryUiState(
            loaded = true,
            stations = stations,
            stationsById = byId,
            collections = catalog.collections,
            favorites = favoriteIds.mapNotNull { byId[it] },
            favoriteIds = favoriteIds.toSet(),
            recents = recentIds.mapNotNull { byId[it] },
            catalogVersion = catalog.version,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibraryUiState())

    val player: StateFlow<PlayerUiState> = playback.state

    /** Measured stream starts, most recent first. */
    val starts: StateFlow<List<StartRecord>> = container.diagnostics.starts

    /** The wake-up alarm. */
    val alarm: StateFlow<AlarmConfig> = container.alarmStore.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.alarmStore.current())

    private val _crashReport = MutableStateFlow<String?>(null)

    /** Details of the last crash, offered once on the next launch. */
    val crashReport: StateFlow<String?> = _crashReport.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _crashReport.value = CrashReporter.lastReport(getApplication<Application>())
        }
    }

    fun dismissCrashReport() {
        _crashReport.value = null
        viewModelScope.launch(Dispatchers.IO) { CrashReporter.clear(getApplication<Application>()) }
    }

    // Playback

    fun play(station: Station, queue: List<Station>) = playback.play(station.id, queue.map { it.id })

    fun loadStation(stationId: String) = playback.load(stationId)

    fun togglePlayPause() = playback.togglePlayPause()

    fun next() = playback.next()

    fun previous() = playback.previous()

    /** Switches to the station at [index] in the current queue (swiping in the player). */
    fun selectStationAt(index: Int) = playback.seekToIndex(index)

    fun retry() = playback.retry()

    /** Prepares a station's connection the moment the user touches it (before the tap completes). */
    fun warmUp(station: Station) {
        container.streamWarmer.warm(container.streamSelector.preferredUrl(station))
    }

    /**
     * Prepares the stations the user is most likely to play next (last played, top favorites,
     * recents). Costs a few kilobytes per station: no audio is downloaded.
     */
    fun warmUpLikelyStations() {
        viewModelScope.launch {
            val state = library.first { it.loaded }
            delay(WARM_UP_DELAY_MS)
            val lastPlayed = libraryRepository.lastPlayed()?.stationId?.let { state.stationsById[it] }
            val likely = (listOfNotNull(lastPlayed) + state.favorites.take(4) + state.recents.take(2))
                .distinctBy { it.id }
                .take(MAX_WARM_STATIONS)
            container.streamWarmer.warm(likely.map { container.streamSelector.preferredUrl(it) })
        }
    }

    fun clearDiagnostics() = container.diagnostics.clear()

    /** Whether the launcher can add station shortcuts to the home screen. */
    fun canPinShortcuts(): Boolean = container.shortcuts.canPin()

    /** Asks the launcher to add a shortcut that plays [station]. */
    fun pinShortcut(station: Station, hebrew: Boolean) {
        container.shortcuts.requestPin(station, hebrew)
    }

    /**
     * Instant start (opt-in): when the app opens with nothing playing (on any network), keeps the
     * last station's stream open so that playing it starts at once. Stopped when the app leaves
     * the screen, after two minutes, or when another station plays.
     */
    fun startInstantStart() {
        instantStartJob?.cancel()
        instantStartJob = viewModelScope.launch {
            if (!settingsRepository.settings.first().instantStart) return@launch
            val state = library.first { it.loaded }
            val playerState = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { player.first { it.connected } } ?: return@launch
            if (playerState.playWhenReady || playerState.isCasting) return@launch
            val station = libraryRepository.lastPlayed()?.stationId?.let { state.stationsById[it] } ?: return@launch
            val url = container.streamSelector.preferredUrl(station)
            val format = station.streams.firstOrNull { it.url == url }?.format
            if (!HotStandby.canHold(url, format)) return@launch
            val resolved = container.streamWarmer.resolve(url)
            // The user may have started playing while the address was being resolved.
            if (player.value.playWhenReady) return@launch
            container.hotStandby.start(station.id, resolved ?: url, setOfNotNull(url, resolved), station.icyCharset)
        }
    }

    fun stopInstantStart() {
        instantStartJob?.cancel()
        instantStartJob = null
        container.hotStandby.stop()
    }

    fun setSleepTimer(minutes: Int) {
        val fadeOut = settings.value?.sleepFadeOut ?: true
        playback.setSleepTimer(minutes * 60_000L, fadeOut)
    }

    fun extendSleepTimer(endElapsedRealtime: Long, extraMinutes: Int) {
        val remaining = endElapsedRealtime - android.os.SystemClock.elapsedRealtime()
        val fadeOut = settings.value?.sleepFadeOut ?: true
        playback.setSleepTimer(remaining.coerceAtLeast(0L) + extraMinutes * 60_000L, fadeOut)
    }

    fun cancelSleepTimer() = playback.cancelSleepTimer()

    // Wake-up alarm

    /** Saves and schedules the alarm; turning it on picks a station when none was chosen. */
    fun updateAlarm(config: AlarmConfig) {
        val updated = if (config.enabled && config.stationId == null) {
            config.copy(stationId = defaultAlarmStationId())
        } else {
            config
        }
        container.alarmStore.save(updated)
        AlarmScheduler.apply(getApplication<Application>(), updated)
    }

    /** Sets the alarm again, e.g. after the user allowed exact alarms in system settings. */
    fun refreshAlarm() {
        AlarmScheduler.apply(getApplication<Application>(), container.alarmStore.current())
    }

    fun canScheduleExactAlarms(): Boolean = AlarmScheduler.canScheduleExact(getApplication<Application>())

    private fun defaultAlarmStationId(): String? {
        val state = library.value
        return libraryRepository.lastPlayed()?.stationId?.takeIf { it in state.stationsById }
            ?: state.favorites.firstOrNull()?.id
            ?: state.stations.firstOrNull()?.id
    }

    // Library

    fun toggleFavorite(stationId: String) {
        viewModelScope.launch { libraryRepository.toggleFavorite(stationId) }
    }

    fun reorderFavorites(stationIds: List<String>) {
        viewModelScope.launch { libraryRepository.setFavorites(stationIds) }
    }

    fun clearRecents() {
        viewModelScope.launch { libraryRepository.clearRecents() }
    }

    fun completeOnboarding(selected: List<String>) {
        viewModelScope.launch {
            if (selected.isNotEmpty()) libraryRepository.addFavorites(selected)
            settingsRepository.setOnboardingDone(true)
        }
    }

    suspend fun probeStream(url: String): StreamProbe.Result = StreamProbe.probe(url)

    fun addCustomStation(name: String, url: String, onAdded: (String) -> Unit) {
        viewModelScope.launch { onAdded(libraryRepository.addCustomStation(name, url)) }
    }

    fun deleteCustomStation(stationId: String) {
        viewModelScope.launch { libraryRepository.deleteCustomStation(stationId) }
    }

    fun refreshCatalog(onResult: (CatalogRepository.RefreshResult) -> Unit) {
        viewModelScope.launch { onResult(catalogRepository.refreshFromRemote()) }
    }

    // Settings

    fun setThemeMode(value: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(value) }
    }

    fun setPureBlack(value: Boolean) {
        viewModelScope.launch { settingsRepository.setPureBlack(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(value) }
    }

    fun setAccentColor(value: Int?) {
        viewModelScope.launch { settingsRepository.setAccentColor(value) }
    }

    fun setStationTint(value: Boolean) {
        viewModelScope.launch { settingsRepository.setStationTint(value) }
    }

    fun setStationLogos(value: Boolean) {
        viewModelScope.launch { settingsRepository.setStationLogos(value) }
    }

    fun setBackgroundGradient(value: Boolean) {
        viewModelScope.launch { settingsRepository.setBackgroundGradient(value) }
    }

    fun setAnimations(value: Boolean) {
        viewModelScope.launch { settingsRepository.setAnimations(value) }
    }

    fun setDataSaver(value: DataSaverMode) {
        viewModelScope.launch { settingsRepository.setDataSaver(value) }
    }

    fun setInstantStart(value: Boolean) {
        settingsRepository.setInstantStart(value)
        if (value) startInstantStart() else stopInstantStart()
    }

    fun setSleepFadeOut(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSleepFadeOut(value) }
    }

    fun setSkipIntros(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSkipIntros(value) }
    }

    fun setResumeOnHeadphones(value: Boolean) {
        viewModelScope.launch { settingsRepository.setResumeOnHeadphones(value) }
    }

    private companion object {
        /** Lets the first frame render before warm-ups use the network. */
        const val WARM_UP_DELAY_MS = 400L
        const val MAX_WARM_STATIONS = 6

        /** How long Instant start waits for the playback service to report what's playing. */
        const val CONNECT_TIMEOUT_MS = 3_000L
    }
}
