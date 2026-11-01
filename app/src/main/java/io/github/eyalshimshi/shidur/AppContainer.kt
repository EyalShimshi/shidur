package io.github.eyalshimshi.shidur

import android.content.Context
import io.github.eyalshimshi.shidur.data.CatalogRepository
import io.github.eyalshimshi.shidur.data.LibraryRepository
import io.github.eyalshimshi.shidur.data.SettingsRepository
import io.github.eyalshimshi.shidur.data.shidurPreferences
import io.github.eyalshimshi.shidur.playback.PlaybackConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

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
    val playbackConnection = PlaybackConnection(appContext)
}
