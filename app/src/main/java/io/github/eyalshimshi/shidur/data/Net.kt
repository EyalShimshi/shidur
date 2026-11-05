package io.github.eyalshimshi.shidur.data

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import io.github.eyalshimshi.shidur.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

// Keep this an app-style name: CyberCDN servers add an opening ad or jingle for browser-like
// User-Agents (measured in November 2026), but not for apps.
val USER_AGENT: String =
    "Shidur/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; +https://github.com/EyalShimshi/shidur)"

const val REPOSITORY_URL = "https://github.com/EyalShimshi/shidur"

/** The privacy policy; Google Play requires a link to it inside the app. */
const val PRIVACY_POLICY_URL = "$REPOSITORY_URL/blob/main/PRIVACY.md"

/** Simple blocking HTTPS GET for small JSON/XML documents. Call from a background thread. */
fun httpGet(url: String, maxBytes: Int = 512 * 1024, timeoutMs: Int = 10_000): String {
    require(url.startsWith("https://")) { "App data must be fetched over HTTPS" }
    val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", USER_AGENT)
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code for $url")
        val bytes = connection.inputStream.use { input -> readLimited(input, maxBytes, url) }
        return String(bytes, Charsets.UTF_8)
    } finally {
        connection.disconnect()
    }
}

private fun readLimited(input: InputStream, maxBytes: Int, url: String): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = input.read(chunk)
        if (read < 0) break
        buffer.write(chunk, 0, read)
        if (buffer.size() > maxBytes) throw IOException("Response too large: $url")
    }
    return buffer.toByteArray()
}

/** True when the app's UI language is Hebrew (per-app language first, then the system locale). */
fun isHebrewUi(context: Context): Boolean {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val language = if (!appLocales.isEmpty) {
        appLocales[0]?.language
    } else {
        context.resources.configuration.locales[0]?.language
    }
    return language == "iw" || language == "he"
}
