package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.playback.AudioRing
import io.github.eyalshimshi.shidur.playback.HotStandby
import io.github.eyalshimshi.shidur.playback.IcyDemuxer
import io.github.eyalshimshi.shidur.playback.IcyTitleDecoder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InstantStartTest {

    @Test
    fun icyMetadataIsRemovedFromTheAudio() {
        val metadata = "StreamTitle='Song';".padEnd(32, '\u0000')
        val stream = Buffer()
            .writeUtf8("abcd").writeByte(2).writeUtf8(metadata)
            .writeUtf8("efgh").writeByte(0)
            .writeUtf8("ij")
        val audio = ByteArrayOutputStream()
        val titles = mutableListOf<String>()

        IcyDemuxer(4).pump(
            source = stream,
            isCancelled = { false },
            onAudio = { bytes, offset, length -> audio.write(bytes, offset, length) },
            onMetadata = { raw -> IcyTitleDecoder.decodeStreamTitle(raw, null)?.let(titles::add) },
        )

        assertEquals("abcdefghij", audio.toString("UTF-8"))
        assertEquals(listOf("Song"), titles)
    }

    @Test
    fun streamsWithoutMetadataPassThrough() {
        val audio = ByteArrayOutputStream()
        IcyDemuxer(null).pump(
            source = Buffer().writeUtf8("plain audio"),
            isCancelled = { false },
            onAudio = { bytes, offset, length -> audio.write(bytes, offset, length) },
            onMetadata = { fail("no metadata expected") },
        )
        assertEquals("plain audio", audio.toString("UTF-8"))
    }

    @Test
    fun readerStartsBehindLiveAndKeepsOnlyTheNewestBytes() {
        val ring = AudioRing(4)
        ring.write("abcdef".toByteArray(), 0, 6)
        ring.startReadingBehindLive(10)

        assertEquals("cdef", readAll(ring, 4))
    }

    @Test
    fun readerStartsTheRequestedDistanceBehindLive() {
        val ring = AudioRing(16)
        ring.write("abcdef".toByteArray(), 0, 6)
        ring.startReadingBehindLive(2)

        assertEquals("ef", readAll(ring, 2))
    }

    @Test
    fun finishedStreamEndsAfterTheRemainingData() {
        val ring = AudioRing(16)
        ring.write("ab".toByteArray(), 0, 2)
        ring.startReadingBehindLive(16)
        ring.finish(null)

        assertEquals("ab", readAll(ring, 2))
        assertEquals(-1, ring.read(ByteArray(4), 0, 4, timeoutMs = 10))
        assertTrue(ring.isFinished())
    }

    @Test(expected = IOException::class)
    fun failedStreamReportsTheError() {
        val ring = AudioRing(16)
        ring.finish(IOException("connection lost"))
        ring.read(ByteArray(4), 0, 4, timeoutMs = 10)
    }

    @Test(expected = SocketTimeoutException::class)
    fun silentStreamTimesOut() {
        AudioRing(16).read(ByteArray(4), 0, 4, timeoutMs = 10)
    }

    @Test
    fun onlyPlainStreamsCanBeHeld() {
        assertTrue(HotStandby.canHold("https://radio.example/live.mp3", "mp3"))
        assertTrue(HotStandby.canHold("https://radio.example/icy/kan88_mp3", null))
        assertFalse(HotStandby.canHold("https://radio.example/hls/playlist.m3u8", "hls"))
        assertFalse(HotStandby.canHold("https://radio.example/listen.pls?id=1", null))
    }

    private fun readAll(ring: AudioRing, expected: Int): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8)
        while (out.size() < expected) {
            val read = ring.read(buffer, 0, buffer.size, timeoutMs = 10)
            if (read < 0) break
            out.write(buffer, 0, read)
        }
        return out.toString("UTF-8")
    }
}
