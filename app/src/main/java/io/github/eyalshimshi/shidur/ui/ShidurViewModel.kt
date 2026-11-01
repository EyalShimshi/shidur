package io.github.eyalshimshi.shidur.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.eyalshimshi.shidur.ShidurApplication
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.DataSaverMode
import io.github.eyalshimshi.shidur.data.ThemeMode
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.StationCollection
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.playback.StreamProbe
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // Playback

    fun play(station: Station, queue: List<Station>) = playback.play(station.id, queue.map { it.id })

    fun loadStation(stationId: String) = playback.load(stationId)

    fun togglePlayPause() = playback.togglePlayPause()

    fun next() = playback.next()

    fun previous() = playback.previous()

    fun retry() = playback.retry()

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

    fun setStationTint(value: Boolean) {
        viewModelScope.launch { settingsRepository.setStationTint(value) }
    }

    fun setDataSaver(value: DataSaverMode) {
        viewModelScope.launch { settingsRepository.setDataSaver(value) }
    }

    fun setSleepFadeOut(value: Boolean) {
        viewModelScope.launch { settingsRepository.setSleepFadeOut(value) }
    }

    fun setResumeOnHeadphones(value: Boolean) {
        viewModelScope.launch { settingsRepository.setResumeOnHeadphones(value) }
    }
}
