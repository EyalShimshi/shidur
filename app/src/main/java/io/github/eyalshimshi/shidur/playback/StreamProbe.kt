package io.github.eyalshimshi.shidur.playback

import io.github.eyalshimshi.shidur.data.USER_AGENT
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Checks a user-provided stream URL and resolves .pls/.m3u playlists to the actual stream. */
object StreamProbe {

    data class Result(
        val streamUrl: String,
        val contentType: String?,
        val stationName: String?,
        val bitrate: String?,
    )

    suspend fun probe(url: String): Result = withContext(Dispatchers.IO) {
        var current = url.trim()
        repeat(MAX_PLAYLIST_HOPS) {
            require(current.startsWith("http://") || current.startsWith("https://")) { "Unsupported URL" }
            val connection = URI.create(current).toURL().openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                connection.setRequestProperty("Icy-MetaData", "1")
                val code = connection.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                val type = connection.contentType?.lowercase(Locale.ROOT)
                val path = URI.create(current).path.orEmpty().lowercase(Locale.ROOT)
                val isPlaylist = type in PLAYLIST_TYPES ||
                    path.endsWith(".pls") ||
                    (path.endsWith(".m3u") && type?.contains("mpegurl") != false)
                if (isPlaylist) {
                    val body = connection.inputStream.use { input ->
                        val bytes = ByteArray(MAX_PLAYLIST_BYTES)
                        var total = 0
                        while (total < bytes.size) {
                            val read = input.read(bytes, total, bytes.size - total)
                            if (read < 0) break
                            total += read
                        }
                        String(bytes, 0, total, Charsets.UTF_8)
                    }
                    current = firstStreamUrl(body) ?: throw IOException("Empty playlist")
                } else {
                    val looksLikeAudio = type == null ||
                        type.startsWith("audio/") ||
                        type.startsWith("application/ogg") ||
                        type.contains("mpegurl") ||
                        type.startsWith("application/octet-stream")
                    if (!looksLikeAudio) throw IOException("Not an audio stream ($type)")
                    return@withContext Result(
                        streamUrl = current,
                        contentType = type,
                        stationName = connection.getHeaderField("icy-name")?.trim()?.takeIf { it.isNotEmpty() },
                        bitrate = connection.getHeaderField("icy-br")?.trim(),
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Too many nested playlists")
    }

    /** Extracts the first http(s) URL from a PLS ("File1=...") or M3U playlist. */
    fun firstStreamUrl(playlist: String): String? = playlist.lineSequence()
        .map { it.trim() }
        .map { line -> if (line.startsWith("File", ignoreCase = true) && '=' in line) line.substringAfter('=').trim() else line }
        .firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

    private const val MAX_PLAYLIST_HOPS = 3
    private const val MAX_PLAYLIST_BYTES = 16 * 1024
    private const val TIMEOUT_MS = 10_000
    private val PLAYLIST_TYPES = setOf("audio/x-scpls", "audio/scpls", "audio/x-mpegurl.pls")
}
