package io.github.eyalshimshi.shidur.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.eyalshimshi.shidur.model.CustomStationDto
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.toStation
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class LastPlayed(val stationId: String, val queue: List<String>)

/** The user's own data: favorites (ordered), recently played, custom stations, last station. */
class LibraryRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites")
        val RECENTS = stringPreferencesKey("recents")
        val CUSTOM = stringPreferencesKey("custom_stations")
        val LAST_STATION = stringPreferencesKey("last_station")
        val LAST_QUEUE = stringPreferencesKey("last_queue")
    }

    private val customSerializer = ListSerializer(CustomStationDto.serializer())

    val favoriteIds: Flow<List<String>> = dataStore.safeData()
        .map { it[Keys.FAVORITES].toIdList() }
        .distinctUntilChanged()

    val recentIds: Flow<List<String>> = dataStore.safeData()
        .map { it[Keys.RECENTS].toIdList() }
        .distinctUntilChanged()

    val customStations: Flow<List<Station>> = dataStore.safeData()
        .map { p -> decodeCustom(p[Keys.CUSTOM]).map { it.toStation() } }
        .distinctUntilChanged()

    suspend fun toggleFavorite(stationId: String) {
        dataStore.edit { p ->
            val list = p[Keys.FAVORITES].toIdList().toMutableList()
            if (!list.remove(stationId)) list.add(stationId)
            p[Keys.FAVORITES] = list.joinToString(SEPARATOR)
        }
    }

    suspend fun setFavorites(stationIds: List<String>) {
        dataStore.edit { it[Keys.FAVORITES] = stationIds.distinct().joinToString(SEPARATOR) }
    }

    suspend fun addFavorites(stationIds: Collection<String>) {
        dataStore.edit { p ->
            val list = p[Keys.FAVORITES].toIdList().toMutableList()
            stationIds.forEach { if (it !in list) list.add(it) }
            p[Keys.FAVORITES] = list.joinToString(SEPARATOR)
        }
    }

    suspend fun recordRecent(stationId: String) {
        dataStore.edit { p ->
            val list = p[Keys.RECENTS].toIdList().toMutableList()
            list.remove(stationId)
            list.add(0, stationId)
            p[Keys.RECENTS] = list.take(MAX_RECENTS).joinToString(SEPARATOR)
        }
    }

    suspend fun removeRecent(stationId: String) {
        dataStore.edit { p ->
            p[Keys.RECENTS] = p[Keys.RECENTS].toIdList().filter { it != stationId }.joinToString(SEPARATOR)
        }
    }

    suspend fun clearRecents() {
        dataStore.edit { it.remove(Keys.RECENTS) }
    }

    /** Adds a custom station, also as a favorite, and returns its id. */
    suspend fun addCustomStation(name: String, url: String): String {
        val id = "custom-" + UUID.randomUUID().toString().substring(0, 8)
        dataStore.edit { p ->
            val stations = decodeCustom(p[Keys.CUSTOM]) + CustomStationDto(id, name.trim(), url.trim())
            p[Keys.CUSTOM] = json.encodeToString(customSerializer, stations)
            val favorites = p[Keys.FAVORITES].toIdList().toMutableList()
            favorites.add(id)
            p[Keys.FAVORITES] = favorites.joinToString(SEPARATOR)
        }
        return id
    }

    suspend fun deleteCustomStation(stationId: String) {
        dataStore.edit { p ->
            val stations = decodeCustom(p[Keys.CUSTOM]).filter { it.id != stationId }
            p[Keys.CUSTOM] = json.encodeToString(customSerializer, stations)
            p[Keys.FAVORITES] = p[Keys.FAVORITES].toIdList().filter { it != stationId }.joinToString(SEPARATOR)
            p[Keys.RECENTS] = p[Keys.RECENTS].toIdList().filter { it != stationId }.joinToString(SEPARATOR)
        }
    }

    suspend fun setLastPlayed(stationId: String, queue: List<String>) {
        dataStore.edit { p ->
            p[Keys.LAST_STATION] = stationId
            p[Keys.LAST_QUEUE] = queue.joinToString(SEPARATOR)
        }
    }

    suspend fun lastPlayed(): LastPlayed? {
        val p = dataStore.safeData().first()
        val stationId = p[Keys.LAST_STATION] ?: return null
        return LastPlayed(stationId, p[Keys.LAST_QUEUE].toIdList())
    }

    private fun decodeCustom(raw: String?): List<CustomStationDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(customSerializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val SEPARATOR = ","
        const val MAX_RECENTS = 20
    }
}

private fun String?.toIdList(): List<String> =
    this?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
