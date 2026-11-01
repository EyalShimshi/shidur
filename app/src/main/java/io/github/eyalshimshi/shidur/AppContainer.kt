package io.github.eyalshimshi.shidur

import android.content.Context
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.LibraryRepository
import io.github.eyalshimshi.shidur.data.SettingsRepository
import io.github.eyalshimshi.shidur.data.shidurPreferences
import io.github.eyalshimshi.shidur.playback.CachingDns
import io.github.eyalshimshi.shidur.playback.HotStandby
import io.github.eyalshimshi.shidur.playback.PlaybackConnection
import io.github.eyalshimshi.shidur.playback.PlaybackDiagnostics
import io.github.eyalshimshi.shidur.playback.StreamSelector
import io.github.eyalshimshi.shidur.playback.StreamWarmer
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/** Manual dependency container shared by the UI, the playback service and the widget. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val preferences = appContext.shidurPreferences()

    val settingsRepository = SettingsRepository(preferences)
    val libraryRepository = LibraryRepository(preferences, json)
    val catalogRepository = CatalogRepository(appContext, json, libraryRepository, applicationScope)

    /** One HTTP stack for streams and warm-ups, so warmed DNS answers and connections are reused. */
    val dns = CachingDns()
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    val streamSelector = StreamSelector(appContext, settingsRepository, applicationScope)
    val streamWarmer = StreamWarmer(httpClient, applicationScope)
    val hotStandby = HotStandby(httpClient)
    val diagnostics = PlaybackDiagnostics()

    val playbackConnection = PlaybackConnection(appContext, diagnostics)
}
