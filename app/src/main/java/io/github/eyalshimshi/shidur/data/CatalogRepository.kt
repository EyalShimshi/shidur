package io.github.eyalshimshi.shidur.data

import android.content.Context
import android.util.Log
import io.github.eyalshimshi.shidur.model.Catalog
import io.github.eyalshimshi.shidur.model.CatalogDto
import io.github.eyalshimshi.shidur.model.SUPPORTED_SCHEMA_VERSION
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.toCatalog
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Loads the station catalog: the snapshot bundled in the APK, a newer copy cached from the
 * repository, and the user's custom stations.
 */
class CatalogRepository(
    private val context: Context,
    private val json: Json,
    private val library: LibraryRepository,
    scope: CoroutineScope,
) {
    enum class RefreshResult { UPDATED, UP_TO_DATE, FAILED }

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog.asStateFlow()

    /** Catalog stations followed by the user's custom stations. */
    val stations: Flow<List<Station>> =
        combine(_catalog.filterNotNull(), library.customStations) { catalog, custom ->
            catalog.stations + custom
        }

    private val loadJob = scope.launch(Dispatchers.IO) { loadLocal() }

    init {
        scope.launch {
            loadJob.join()
            refreshFromRemote()
        }
    }

    suspend fun awaitStations(): List<Station> = stations.first()

    suspend fun findStation(stationId: String): Station? = awaitStations().firstOrNull { it.id == stationId }

    /** Downloads the catalog published in the repository and applies it when it is newer. */
    suspend fun refreshFromRemote(): RefreshResult = withContext(Dispatchers.IO) {
        loadJob.join()
        val text = try {
            httpGet(REMOTE_URL)
        } catch (e: Exception) {
            Log.i(TAG, "Catalog refresh skipped: ${e.message}")
            return@withContext RefreshResult.FAILED
        }
        val remote = runCatching { parse(text) }.getOrNull() ?: return@withContext RefreshResult.FAILED
        val current = _catalog.value
        if (current != null && remote.version <= current.version) return@withContext RefreshResult.UP_TO_DATE
        // Guard against an accidentally truncated catalog.
        if (current != null && remote.stations.size < current.stations.size * 7 / 10) {
            return@withContext RefreshResult.FAILED
        }
        runCatching { cacheFile().writeText(text, Charsets.UTF_8) }
        _catalog.value = remote
        RefreshResult.UPDATED
    }

    private fun loadLocal() {
        val bundled = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.mapCatching { parse(it) }
            .onFailure { Log.w(TAG, "Couldn't read the bundled catalog", it) }
            .getOrNull()
        val cached = runCatching {
            val file = cacheFile()
            if (file.exists()) parse(file.readText(Charsets.UTF_8)) else null
        }.getOrNull()
        val best = listOfNotNull(bundled, cached).maxByOrNull { it.version }
        _catalog.value = best ?: Catalog(version = 0, collections = emptyList(), stations = emptyList())
    }

    private fun parse(text: String): Catalog? {
        val dto = json.decodeFromString(CatalogDto.serializer(), text)
        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) return null
        return dto.toCatalog().takeIf { it.stations.isNotEmpty() }
    }

    private fun cacheFile() = File(context.filesDir, "catalog.json")

    companion object {
        private const val TAG = "CatalogRepository"
        const val ASSET_NAME = "catalog.json"

        /** The catalog file in this repository. Only reachable while the repository is public. */
        const val REMOTE_URL =
            "https://raw.githubusercontent.com/EyalShimshi/shidur/main/app/src/main/assets/catalog.json"
    }
}
