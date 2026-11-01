package io.github.eyalshimshi.shidur.playback

import android.content.Context
import android.net.ConnectivityManager
import io.github.eyalshimshi.shidur.data.DataSaverMode
import io.github.eyalshimshi.shidur.data.SettingsRepository
import io.github.eyalshimshi.shidur.model.Station
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Picks which of a station's stream addresses to try first: the lowest bitrate when data saver
 * applies, otherwise the address that worked last time (so a dead first address costs time only once).
 */
class StreamSelector(
    context: Context,
    settings: SettingsRepository,
    scope: CoroutineScope,
) {
    private val prefs = context.getSharedPreferences("shidur_streams", Context.MODE_PRIVATE)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    @Volatile
    private var dataSaver = DataSaverMode.MOBILE

    init {
        scope.launch { settings.settings.collect { dataSaver = it.dataSaver } }
    }

    fun preferredIndex(station: Station): Int {
        if (dataSaverActive()) {
            station.streams.withIndex()
                .filter { it.value.bitrate != null }
                .minByOrNull { it.value.bitrate ?: Int.MAX_VALUE }
                ?.let { return it.index }
        }
        val lastWorking = prefs.getString(KEY_PREFIX + station.id, null) ?: return 0
        return station.streams.indexOfFirst { it.url == lastWorking }.coerceAtLeast(0)
    }

    fun preferredUrl(station: Station): String = station.streams[preferredIndex(station)].url

    fun rememberWorking(station: Station, index: Int) {
        val url = station.streams.getOrNull(index)?.url ?: return
        if (prefs.getString(KEY_PREFIX + station.id, null) != url) {
            prefs.edit().putString(KEY_PREFIX + station.id, url).apply()
        }
    }

    private fun dataSaverActive(): Boolean = when (dataSaver) {
        DataSaverMode.OFF -> false
        DataSaverMode.ALWAYS -> true
        DataSaverMode.MOBILE -> connectivity?.isActiveNetworkMetered == true
    }

    private companion object {
        const val KEY_PREFIX = "working_"
    }
}
