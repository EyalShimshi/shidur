package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.playback.IcyTitleDecoder
import io.github.eyalshimshi.shidur.playback.StreamProbe
import java.nio.charset.Charset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcyAndPlaylistTest {

    @Test
    fun decodesUtf8Title() {
        val raw = "StreamTitle='עומר אדם - שיר';StreamUrl='';".toByteArray(Charsets.UTF_8)
        assertEquals("עומר אדם - שיר", IcyTitleDecoder.decodeStreamTitle(raw, null))
    }

    @Test
    fun detectsWindows1255Hebrew() {
        val raw = "StreamTitle='עומר אדם - שיר';".toByteArray(Charset.forName("windows-1255"))
        assertEquals("עומר אדם - שיר", IcyTitleDecoder.decodeStreamTitle(raw, null))
    }

    @Test
    fun usesCharsetHint() {
        val raw = "StreamTitle='Artist - Song';".toByteArray(Charsets.UTF_8)
        assertEquals("Artist - Song", IcyTitleDecoder.decodeStreamTitle(raw, "utf-8"))
    }

    @Test
    fun ignoresPaddingAndEmptyTitles() {
        val padded = "StreamTitle='Song';".toByteArray(Charsets.UTF_8) + ByteArray(6)
        assertEquals("Song", IcyTitleDecoder.decodeStreamTitle(padded, null))
        assertNull(IcyTitleDecoder.decodeStreamTitle("StreamTitle=' - ';".toByteArray(), null))
        assertNull(IcyTitleDecoder.decodeStreamTitle("StreamUrl='x';".toByteArray(), null))
    }

    @Test
    fun parsesPlsPlaylist() {
        val pls = "[playlist]\nNumberOfEntries=1\nFile1=http://radio.example/live\nTitle1=Example\n"
        assertEquals("http://radio.example/live", StreamProbe.firstStreamUrl(pls))
    }

    @Test
    fun parsesM3uPlaylist() {
        val m3u = "#EXTM3U\n#EXTINF:-1,Example\nhttps://radio.example/stream.mp3\n"
        assertEquals("https://radio.example/stream.mp3", StreamProbe.firstStreamUrl(m3u))
    }
}
