package io.github.eyalshimshi.shidur.ui

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import io.github.eyalshimshi.shidur.BuildConfig
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.REPOSITORY_URL
import io.github.eyalshimshi.shidur.model.Station
import java.net.URLEncoder

fun shareStation(context: Context, station: Station, hebrew: Boolean, nowPlaying: String? = null) {
    val lines = buildList {
        add(context.getString(R.string.share_text, station.name(hebrew)))
        nowPlaying?.let { add("♪ $it") }
        station.website?.let { add(it) }
    }
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    context.startActivity(Intent.createChooser(send, null))
}

/** Opens a music app's search (or a web search) for a song title. */
fun searchSong(context: Context, title: String) {
    val candidates = listOf(
        Intent(MediaStore.INTENT_ACTION_MEDIA_SEARCH).putExtra(SearchManager.QUERY, title),
        Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, title),
    )
    for (intent in candidates) {
        try {
            context.startActivity(intent)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next option.
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
    Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
}

/** A pre-filled GitHub issue for a problem with a station (or the app when [station] is null). */
fun reportProblemUrl(station: Station?, streamIndex: Int? = null): String {
    val title = if (station != null) "Problem with ${station.nameEn} (${station.id})" else "Problem report"
    val body = buildString {
        appendLine("**What happened?**")
        appendLine()
        appendLine()
        appendLine("---")
        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        if (station != null) {
            appendLine("Station: ${station.id}")
            val stream = station.streams.getOrNull(streamIndex ?: 0)
            if (stream != null && !station.isCustom) appendLine("Stream: ${stream.url}")
        }
    }
    return "$REPOSITORY_URL/issues/new?title=${encode(title)}&body=${encode(body)}"
}

fun suggestStationUrl(): String =
    "$REPOSITORY_URL/issues/new?title=${encode("Station suggestion: ")}&body=${encode("Station name:\nWebsite:\nStream URL (if known):\n")}"

private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
