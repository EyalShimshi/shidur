package io.github.eyalshimshi.shidur.data

import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

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

class SettingsRepository(private val prefs: SharedPreferences) {

    private object Keys {
        const val THEME = "theme_mode"
        const val PURE_BLACK = "pure_black"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val STATION_TINT = "station_tint"
        const val DATA_SAVER = "data_saver"
        const val SLEEP_FADE = "sleep_fade"
        const val RESUME_HEADPHONES = "resume_headphones"
        const val ONBOARDING_DONE = "onboarding_done"
    }

    val settings: Flow<AppSettings> = prefs
        .observe { p ->
            AppSettings(
                themeMode = p.getString(Keys.THEME, null).toEnum(ThemeMode.SYSTEM),
                pureBlack = p.getBoolean(Keys.PURE_BLACK, false),
                dynamicColor = p.getBoolean(Keys.DYNAMIC_COLOR, true),
                stationTint = p.getBoolean(Keys.STATION_TINT, true),
                dataSaver = p.getString(Keys.DATA_SAVER, null).toEnum(DataSaverMode.MOBILE),
                sleepFadeOut = p.getBoolean(Keys.SLEEP_FADE, true),
                resumeOnHeadphones = p.getBoolean(Keys.RESUME_HEADPHONES, false),
                onboardingDone = p.getBoolean(Keys.ONBOARDING_DONE, false),
            )
        }
        .distinctUntilChanged()

    fun setThemeMode(value: ThemeMode) = putString(Keys.THEME, value.name)

    fun setPureBlack(value: Boolean) = putBoolean(Keys.PURE_BLACK, value)

    fun setDynamicColor(value: Boolean) = putBoolean(Keys.DYNAMIC_COLOR, value)

    fun setStationTint(value: Boolean) = putBoolean(Keys.STATION_TINT, value)

    fun setDataSaver(value: DataSaverMode) = putString(Keys.DATA_SAVER, value.name)

    fun setSleepFadeOut(value: Boolean) = putBoolean(Keys.SLEEP_FADE, value)

    fun setResumeOnHeadphones(value: Boolean) = putBoolean(Keys.RESUME_HEADPHONES, value)

    fun setOnboardingDone(value: Boolean) = putBoolean(Keys.ONBOARDING_DONE, value)

    private fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
