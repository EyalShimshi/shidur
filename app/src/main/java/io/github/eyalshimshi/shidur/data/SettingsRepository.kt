package io.github.eyalshimshi.shidur.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DataSaverMode { OFF, MOBILE, ALWAYS }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = true,
    val stationTint: Boolean = true,
    val dataSaver: DataSaverMode = DataSaverMode.MOBILE,
    val sleepFadeOut: Boolean = true,
    val resumeOnHeadphones: Boolean = false,
    val onboardingDone: Boolean = false,
)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val PURE_BLACK = booleanPreferencesKey("pure_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val STATION_TINT = booleanPreferencesKey("station_tint")
        val DATA_SAVER = stringPreferencesKey("data_saver")
        val SLEEP_FADE = booleanPreferencesKey("sleep_fade")
        val RESUME_HEADPHONES = booleanPreferencesKey("resume_headphones")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = dataStore.safeData()
        .map { p ->
            AppSettings(
                themeMode = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
                pureBlack = p[Keys.PURE_BLACK] ?: false,
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
                stationTint = p[Keys.STATION_TINT] ?: true,
                dataSaver = p[Keys.DATA_SAVER].toEnum(DataSaverMode.MOBILE),
                sleepFadeOut = p[Keys.SLEEP_FADE] ?: true,
                resumeOnHeadphones = p[Keys.RESUME_HEADPHONES] ?: false,
                onboardingDone = p[Keys.ONBOARDING_DONE] ?: false,
            )
        }
        .distinctUntilChanged()

    suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[Keys.THEME] = value.name }
    }

    suspend fun setPureBlack(value: Boolean) {
        dataStore.edit { it[Keys.PURE_BLACK] = value }
    }

    suspend fun setDynamicColor(value: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = value }
    }

    suspend fun setStationTint(value: Boolean) {
        dataStore.edit { it[Keys.STATION_TINT] = value }
    }

    suspend fun setDataSaver(value: DataSaverMode) {
        dataStore.edit { it[Keys.DATA_SAVER] = value.name }
    }

    suspend fun setSleepFadeOut(value: Boolean) {
        dataStore.edit { it[Keys.SLEEP_FADE] = value }
    }

    suspend fun setResumeOnHeadphones(value: Boolean) {
        dataStore.edit { it[Keys.RESUME_HEADPHONES] = value }
    }

    suspend fun setOnboardingDone(value: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_DONE] = value }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
