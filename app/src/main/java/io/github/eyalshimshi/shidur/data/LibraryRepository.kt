package io.github.eyalshimshi.shidur.data

import android.content.SharedPreferences
import io.github.eyalshimshi.shidur.model.CustomStationDto
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.toStation
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

data class LastPlayed(val stationId: String, val queue: List<String>)

/** The user's own data: favorites (ordered), recently played, custom stations, last station. */
class LibraryRepository(
    private val prefs: SharedPreferences,
    private val json: Json,
) {
    private object Keys {
        const val FAVORITES = "favorites"
        const val RECENTS = "recents"
        const val CUSTOM = "custom_stations"
        const val LAST_STATION = "last_station"
        const val LAST_QUEUE = "last_queue"
    }

    private val customSerializer = ListSerializer(CustomStationDto.serializer())
    private val lock = Any()

    val favoriteIds: Flow<List<String>> = prefs.observe { it.ids(Keys.FAVORITES) }.distinctUntilChanged()

    val recentIds: Flow<List<String>> = prefs.observe { it.ids(Keys.RECENTS) }.distinctUntilChanged()

    val customStations: Flow<List<Station>> = prefs
        .observe { p -> decodeCustom(p.getString(Keys.CUSTOM, null)).map { it.toStation() } }
        .distinctUntilChanged()

    fun toggleFavorite(stationId: String) = update { editor ->
        val list = prefs.ids(Keys.FAVORITES).toMutableList()
        if (!list.remove(stationId)) list.add(stationId)
        editor.putIds(Keys.FAVORITES, list)
    }

    fun setFavorites(stationIds: List<String>) = update { editor ->
        editor.putIds(Keys.FAVORITES, stationIds.distinct())
    }

    fun addFavorites(stationIds: Collection<String>) = update { editor ->
        val list = prefs.ids(Keys.FAVORITES).toMutableList()
        stationIds.forEach { if (it !in list) list.add(it) }
        editor.putIds(Keys.FAVORITES, list)
    }

    fun recordRecent(stationId: String) = update { editor ->
        val list = prefs.ids(Keys.RECENTS).toMutableList()
        list.remove(stationId)
        list.add(0, stationId)
        editor.putIds(Keys.RECENTS, list.take(MAX_RECENTS))
    }

    fun removeRecent(stationId: String) = update { editor ->
        editor.putIds(Keys.RECENTS, prefs.ids(Keys.RECENTS).filter { it != stationId })
    }

    fun clearRecents() = update { editor ->
        editor.remove(Keys.RECENTS)
    }

    /** Adds a custom station, also as a favorite, and returns its id. */
    fun addCustomStation(name: String, url: String): String {
        val id = "custom-" + UUID.randomUUID().toString().substring(0, 8)
        update { editor ->
            val stations = decodeCustom(prefs.getString(Keys.CUSTOM, null)) +
                CustomStationDto(id, name.trim(), url.trim())
            editor.putString(Keys.CUSTOM, json.encodeToString(customSerializer, stations))
            editor.putIds(Keys.FAVORITES, prefs.ids(Keys.FAVORITES) + id)
        }
        return id
    }

    fun deleteCustomStation(stationId: String) = update { editor ->
        val stations = decodeCustom(prefs.getString(Keys.CUSTOM, null)).filter { it.id != stationId }
        editor.putString(Keys.CUSTOM, json.encodeToString(customSerializer, stations))
        editor.putIds(Keys.FAVORITES, prefs.ids(Keys.FAVORITES).filter { it != stationId })
        editor.putIds(Keys.RECENTS, prefs.ids(Keys.RECENTS).filter { it != stationId })
    }

    fun setLastPlayed(stationId: String, queue: List<String>) = update { editor ->
        editor.putString(Keys.LAST_STATION, stationId)
        editor.putIds(Keys.LAST_QUEUE, queue)
    }

    fun lastPlayed(): LastPlayed? {
        val stationId = prefs.getString(Keys.LAST_STATION, null) ?: return null
        return LastPlayed(stationId, prefs.ids(Keys.LAST_QUEUE))
    }

    /** Read-modify-write under a lock, so the UI and the playback service can't overwrite each other. */
    private inline fun update(block: (SharedPreferences.Editor) -> Unit) {
        synchronized(lock) {
            val editor = prefs.edit()
            block(editor)
            editor.apply()
        }
    }

    private fun decodeCustom(raw: String?): List<CustomStationDto> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(customSerializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_RECENTS = 20
    }
}

private fun SharedPreferences.ids(key: String): List<String> =
    getString(key, null)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

private fun SharedPreferences.Editor.putIds(key: String, ids: List<String>) {
    putString(key, ids.joinToString(","))
}
