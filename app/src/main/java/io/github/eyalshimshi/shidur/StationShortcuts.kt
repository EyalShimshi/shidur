package io.github.eyalshimshi.shidur

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.MonogramRenderer
import io.github.eyalshimshi.shidur.playback.StationLogos

/**
 * Launcher shortcuts: the top favorites when the app icon is long-pressed, and stations pinned to
 * the home screen. Tapping one opens the app and plays the station.
 */
class StationShortcuts(private val context: Context, private val logos: StationLogos) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Shows the first few [favorites] as app shortcuts; does nothing when they haven't changed. */
    fun publishFavorites(favorites: List<Station>, hebrew: Boolean) {
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceIn(0, MAX_SHORTCUTS)
        val shown = favorites.take(max)
        val signature = shown.joinToString("|") { "${it.id}:${it.name(hebrew)}:${logos.file(it)?.name}" }
        if (prefs.getString(KEY_SIGNATURE, null) == signature) return
        val published = runCatching {
            ShortcutManagerCompat.setDynamicShortcuts(context, shown.map { shortcut(it, hebrew) })
        }.getOrDefault(false)
        if (published) prefs.edit().putString(KEY_SIGNATURE, signature).apply()
    }

    fun canPin(): Boolean = runCatching { ShortcutManagerCompat.isRequestPinShortcutSupported(context) }.getOrDefault(false)

    /** Asks the launcher to add [station] to the home screen; the launcher confirms with the user. */
    fun requestPin(station: Station, hebrew: Boolean): Boolean = runCatching {
        ShortcutManagerCompat.requestPinShortcut(context, shortcut(station, hebrew), null)
    }.getOrDefault(false)

    private fun shortcut(station: Station, hebrew: Boolean): ShortcutInfoCompat {
        val name = station.name(hebrew)
        return ShortcutInfoCompat.Builder(context, ID_PREFIX + station.id)
            .setShortLabel(name)
            .setLongLabel(name)
            .setIcon(IconCompat.createWithAdaptiveBitmap(iconBitmap(station)))
            .setIntent(playIntent(context, station.id))
            .build()
    }

    /** A full-bleed adaptive icon with the artwork kept clear of the launcher's mask. */
    private fun iconBitmap(station: Station): Bitmap {
        val logo = logos.file(station)?.let { BitmapFactory.decodeFile(it.path) }
            ?: return MonogramRenderer.render(station, ICON_PX, contentScale = MONOGRAM_SCALE)
        val icon = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(icon)
        canvas.drawColor(logo.getPixel(1, 1))
        val inset = ICON_PX * (1f - LOGO_SCALE) / 2f
        canvas.drawBitmap(
            logo,
            null,
            RectF(inset, inset, ICON_PX - inset, ICON_PX - inset),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        logo.recycle()
        return icon
    }

    companion object {
        private const val PREFS = "shidur_shortcuts"
        private const val KEY_SIGNATURE = "favorites_signature"
        private const val ID_PREFIX = "station-"
        private const val MAX_SHORTCUTS = 4

        /** Adaptive icons are 108 dp; launchers show the middle 72 dp, often as a circle. */
        private const val ICON_PX = 432
        private const val MONOGRAM_SCALE = 72f / 108f
        private const val LOGO_SCALE = 0.6f

        /** Opens the app and plays [stationId]. */
        fun playIntent(context: Context, stationId: String): Intent =
            Intent(Intent.ACTION_VIEW, Uri.parse("shidur://station/$stationId"), context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PLAY_STATION, true)
    }
}
