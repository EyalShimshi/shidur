package io.github.eyalshimshi.shidur.playback

import android.os.SystemClock
import io.github.eyalshimshi.shidur.data.USER_AGENT
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/** DNS answers kept for a few minutes, so a warmed-up station connects without a lookup. */
class CachingDns(
    private val ttlMs: Long = 3 * 60_000L,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    private val upstream: Dns = Dns.SYSTEM,
) : Dns {

    private class Entry(val addresses: List<InetAddress>, val resolvedAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = clock()
        cache[hostname]?.let { entry -> if (now - entry.resolvedAt < ttlMs) return entry.addresses }
        val addresses = upstream.lookup(hostname)
        cache[hostname] = Entry(addresses, now)
        return addresses
    }

    fun invalidate(hostname: String) {
        cache.remove(hostname)
    }

    fun clear() {
        cache.clear()
    }
}

/**
 * Prepares likely stations before they are played: resolves the DNS name, opens (and, when the
 * server allows it, keeps) a TLS connection, and follows redirectors such as Triton's
 * `livestream-redirect`, so playback can go straight to the final server.
 *
 * Only HEAD requests are sent: no audio is downloaded and no listener session is opened.
 */
class StreamWarmer(
    client: OkHttpClient,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val probeClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private class Resolved(val url: String, val at: Long)

    private val resolved = ConcurrentHashMap<String, Resolved>()
    private val inFlight = ConcurrentHashMap<String, Deferred<String?>>()
    private val permits = Semaphore(MAX_PARALLEL)

    /** The final address for [url] if it was resolved recently, otherwise null. */
    fun resolvedUrl(url: String): String? {
        val entry = resolved[url] ?: return null
        if (clock() - entry.at > RESOLVED_TTL_MS) {
            resolved.remove(url)
            return null
        }
        return entry.url
    }

    /** Drops a resolved address, e.g. after the server rejected it. */
    fun forget(url: String) {
        resolved.remove(url)
    }

    /** Starts warming [url] in the background; does nothing if it is fresh or already in progress. */
    fun warm(url: String) {
        if (resolvedUrl(url) != null || inFlight.containsKey(url)) return
        scope.launch { resolve(url) }
    }

    fun warm(urls: Collection<String>) {
        urls.forEach(::warm)
    }

    /** Waits up to [timeoutMs] for a warm-up that is already running; never starts a new one. */
    suspend fun awaitInFlight(url: String, timeoutMs: Long): String? {
        resolvedUrl(url)?.let { return it }
        val pending = inFlight[url] ?: return null
        return withTimeoutOrNull(timeoutMs) { pending.await() }
    }

    /** Resolves [url] (or returns the cached result) and returns the final address, or null. */
    suspend fun resolve(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        resolvedUrl(url)?.let { return it }
        val fresh = scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                permits.withPermit { followRedirects(url) }?.also { resolved[url] = Resolved(it, clock()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            } finally {
                inFlight.remove(url)
            }
        }
        val existing = inFlight.putIfAbsent(url, fresh)
        if (existing != null) {
            fresh.cancel()
            return existing.await()
        }
        fresh.start()
        return fresh.await()
    }

    private fun followRedirects(start: String): String? {
        var current = start
        repeat(MAX_HOPS) {
            val request = try {
                Request.Builder()
                    .url(current)
                    .head()
                    .header("User-Agent", USER_AGENT)
                    .build()
            } catch (e: IllegalArgumentException) {
                return null
            }
            val response = try {
                probeClient.newCall(request).execute()
            } catch (e: IOException) {
                return null
            }
            response.use {
                if (!it.isRedirect) return current
                val location = it.header("Location") ?: return current
                current = it.request.url.resolve(location)?.toString() ?: return current
            }
        }
        return current
    }

    private companion object {
        const val MAX_HOPS = 4
        const val MAX_PARALLEL = 3
        const val RESOLVED_TTL_MS = 3 * 60_000L
    }
}
