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
    /** Seed of the app's colors when [dynamicColor] is off; null is the Material baseline purple. */
    val accentColor: Int? = null,
    val stationTint: Boolean = true,
    /** Shows stations' logos (downloaded once); off keeps the text artwork. */
    val stationLogos: Boolean = true,
    val backgroundGradient: Boolean = true,
    val animations: Boolean = true,
    val dataSaver: DataSaverMode = DataSaverMode.OFF,
    /**
     * Keeps the last station's stream open while the app is open, so it starts instantly. On by
     * default; it never runs while Data saver is on.
     */
    val instantStart: Boolean = true,
    /** Skips the clip (usually an ad) some stations play to every new listener; see StreamIntro. */
    val skipIntros: Boolean = true,
    /** Shows technical details: start times, stream addresses and the station list version. */
    val expertMode: Boolean = false,
    val sleepFadeOut: Boolean = true,
    val resumeOnHeadphones: Boolean = false,
    val onboardingDone: Boolean = false,
)

class SettingsRepository(private val prefs: SharedPreferences) {

    private object Keys {
        const val THEME = "theme_mode"
        const val PURE_BLACK = "pure_black"
        const val DYNAMIC_COLOR = "dynamic_color"
        const val ACCENT_COLOR = "accent_color"
        const val STATION_TINT = "station_tint"
        const val STATION_LOGOS = "station_logos"
        const val BACKGROUND_GRADIENT = "background_gradient"
        const val ANIMATIONS = "animations"
        const val DATA_SAVER = "data_saver"
        const val INSTANT_START = "instant_start"
        const val SKIP_INTROS = "skip_intros"
        const val EXPERT_MODE = "expert_mode"
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
                accentColor = if (p.contains(Keys.ACCENT_COLOR)) p.getInt(Keys.ACCENT_COLOR, 0) else null,
                stationTint = p.getBoolean(Keys.STATION_TINT, true),
                stationLogos = p.getBoolean(Keys.STATION_LOGOS, true),
                backgroundGradient = p.getBoolean(Keys.BACKGROUND_GRADIENT, true),
                animations = p.getBoolean(Keys.ANIMATIONS, true),
                dataSaver = p.getString(Keys.DATA_SAVER, null).toEnum(DataSaverMode.OFF),
                instantStart = p.getBoolean(Keys.INSTANT_START, true),
                skipIntros = p.getBoolean(Keys.SKIP_INTROS, true),
                expertMode = p.getBoolean(Keys.EXPERT_MODE, false),
                sleepFadeOut = p.getBoolean(Keys.SLEEP_FADE, true),
                resumeOnHeadphones = p.getBoolean(Keys.RESUME_HEADPHONES, false),
                onboardingDone = p.getBoolean(Keys.ONBOARDING_DONE, false),
            )
        }
        .distinctUntilChanged()

    fun setThemeMode(value: ThemeMode) = putString(Keys.THEME, value.name)

    fun setPureBlack(value: Boolean) = putBoolean(Keys.PURE_BLACK, value)

    fun setDynamicColor(value: Boolean) = putBoolean(Keys.DYNAMIC_COLOR, value)

    /** Picks the app's accent color (null for the default one); this turns wallpaper colors off. */
    fun setAccentColor(value: Int?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(Keys.ACCENT_COLOR) else editor.putInt(Keys.ACCENT_COLOR, value)
        editor.putBoolean(Keys.DYNAMIC_COLOR, false).apply()
    }

    fun setStationTint(value: Boolean) = putBoolean(Keys.STATION_TINT, value)

    fun setStationLogos(value: Boolean) = putBoolean(Keys.STATION_LOGOS, value)

    fun setBackgroundGradient(value: Boolean) = putBoolean(Keys.BACKGROUND_GRADIENT, value)

    fun setAnimations(value: Boolean) = putBoolean(Keys.ANIMATIONS, value)

    fun setDataSaver(value: DataSaverMode) = putString(Keys.DATA_SAVER, value.name)

    fun setInstantStart(value: Boolean) = putBoolean(Keys.INSTANT_START, value)

    fun setSkipIntros(value: Boolean) = putBoolean(Keys.SKIP_INTROS, value)

    fun setExpertMode(value: Boolean) = putBoolean(Keys.EXPERT_MODE, value)

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
