package io.github.eyalshimshi.shidur.playback

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Decodes ICY "StreamTitle" metadata. Media3 decodes ICY text as UTF-8 with a Latin-1 fallback,
 * which garbles Hebrew sent as Windows-1255, so the raw bytes are decoded here instead.
 */
object IcyTitleDecoder {

    private val WINDOWS_1255: Charset = Charset.forName("windows-1255")
    private val STREAM_TITLE = Regex("""StreamTitle='(.*?)';""", RegexOption.DOT_MATCHES_ALL)

    fun decodeStreamTitle(raw: ByteArray, charsetHint: String?): String? {
        val charset = when (charsetHint?.lowercase(Locale.ROOT)) {
            "utf-8", "utf8" -> Charsets.UTF_8
            "windows-1255", "cp1255" -> WINDOWS_1255
            "iso-8859-1", "latin1" -> Charsets.ISO_8859_1
            else -> if (isValidUtf8(raw)) Charsets.UTF_8 else WINDOWS_1255
        }
        val text = String(raw, charset)
        val title = STREAM_TITLE.find(text)?.groupValues?.get(1) ?: return null
        return clean(title)
    }

    /** Trims padding and separators; returns null for empty titles. */
    fun clean(title: String): String? {
        val cleaned = title.replace('\u0000', ' ').trim().trim('-', '|', ' ')
        return cleaned.takeIf { it.isNotEmpty() }
    }

    fun isValidUtf8(bytes: ByteArray): Boolean {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }
    }
}
