package io.github.eyalshimshi.shidur

import io.github.eyalshimshi.shidur.playback.CachingDns
import io.github.eyalshimshi.shidur.playback.PlaybackDiagnostics
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PerformanceTest {

    private var now = 0L
    private var lookups = 0

    private val upstream = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            lookups++
            return listOf(InetAddress.getByAddress(hostname, byteArrayOf(10, 0, 0, lookups.toByte())))
        }
    }

    private val dns = CachingDns(ttlMs = 1_000, clock = { now }, upstream = upstream)
    private val diagnostics = PlaybackDiagnostics(clock = { now })

    @Test
    fun dnsAnswersAreReusedWithinTheirLifetime() {
        dns.lookup("radio.example")
        now = 999
        dns.lookup("radio.example")
        assertEquals(1, lookups)
    }

    @Test
    fun dnsAnswersExpire() {
        dns.lookup("radio.example")
        now = 1_000
        dns.lookup("radio.example")
        assertEquals(2, lookups)
    }

    @Test
    fun invalidatedDnsAnswersAreLookedUpAgain() {
        dns.lookup("radio.example")
        dns.invalidate("radio.example")
        dns.lookup("radio.example")
        assertEquals(2, lookups)
    }

    @Test
    fun startIsMeasuredFromRequestToAudio() {
        diagnostics.markRequested("kan-88")
        now = 640
        val record = diagnostics.onAudioStarted("kan-88", "host", warmed = true, skippedRedirect = false, attempts = 1)
        assertEquals(640L, record?.millis)
        assertEquals(record, diagnostics.starts.value.first())
    }

    @Test
    fun audioWithoutARequestIsNotAStart() {
        assertNull(diagnostics.onAudioStarted("kan-88", "host", warmed = false, skippedRedirect = false, attempts = 1))
    }

    @Test
    fun laterServiceMarkDoesNotResetTheUsersTap() {
        diagnostics.markRequested("glglz")
        now = 300
        diagnostics.markIfAbsent("glglz")
        now = 500
        val record = diagnostics.onAudioStarted("glglz", "host", warmed = false, skippedRedirect = false, attempts = 1)
        assertEquals(500L, record?.millis)
    }

    @Test
    fun staleRequestsAreIgnored() {
        diagnostics.markRequested("glz")
        now = 61_000
        assertNull(diagnostics.onAudioStarted("glz", "host", warmed = false, skippedRedirect = false, attempts = 1))
    }
}
