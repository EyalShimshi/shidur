package io.github.eyalshimshi.shidur.playback

import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.formatFrequency

@OptIn(UnstableApi::class)
object MediaItemFactory {
    const val ROOT_ID = "root"
    const val FAVORITES_ID = "favorites"
    const val RECENTS_ID = "recents"
    const val CATEGORIES_ID = "categories"
    const val ALL_ID = "all"
    const val COLLECTION_PREFIX = "collection:"

    /** Browse-tree children are "parentId/stationId" so a tap can queue the whole folder. */
    private const val PATH_SEPARATOR = '/'

    fun browseId(parentId: String, stationId: String) = "$parentId$PATH_SEPARATOR$stationId"

    /** Splits a media id into (parentId or null, stationId). */
    fun parseId(mediaId: String): Pair<String?, String> {
        val index = mediaId.lastIndexOf(PATH_SEPARATOR)
        return if (index < 0) null to mediaId else mediaId.substring(0, index) to mediaId.substring(index + 1)
    }

    fun playable(
        context: Context,
        station: Station,
        hebrew: Boolean,
        streamIndex: Int = 0,
        nowPlaying: String? = null,
        mediaId: String = station.id,
        resolvedUrl: String? = null,
    ): MediaItem {
        val index = streamIndex.coerceIn(0, station.streams.lastIndex)
        val stream = station.streams[index]
        val uri = resolvedUrl ?: stream.url
        val isHls = stream.format.equals("hls", ignoreCase = true) ||
            stream.url.contains(".m3u8", ignoreCase = true) ||
            uri.contains(".m3u8", ignoreCase = true)
        val metadata = metadata(
            context = context,
            station = station,
            hebrew = hebrew,
            streamIndex = index,
            nowPlaying = nowPlaying,
            warmed = resolvedUrl != null,
            skippedRedirect = resolvedUrl != null && resolvedUrl != stream.url,
        )
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMimeType(if (isHls) MimeTypes.APPLICATION_M3U8 else null)
            // Lets Instant start and the intro skip recognize the station and which of its addresses this is.
            .setCustomCacheKey(if (isHls) null else HotStandby.stationKey(station.id, index))
            .setMediaMetadata(metadata)
            .build()
    }

    /** The same item with new now-playing text. The URI is kept, so playback isn't interrupted. */
    fun withNowPlaying(
        context: Context,
        item: MediaItem,
        station: Station,
        hebrew: Boolean,
        nowPlaying: String?,
    ): MediaItem {
        val extras = item.mediaMetadata.extras
        val metadata = metadata(
            context = context,
            station = station,
            hebrew = hebrew,
            streamIndex = extras?.getInt(ShidurCommands.EXTRA_STREAM_INDEX) ?: 0,
            nowPlaying = nowPlaying,
            warmed = extras?.getBoolean(ShidurCommands.EXTRA_WARMED) ?: false,
            skippedRedirect = extras?.getBoolean(ShidurCommands.EXTRA_SKIPPED_REDIRECT) ?: false,
        )
        return item.buildUpon().setMediaMetadata(metadata).build()
    }

    private fun metadata(
        context: Context,
        station: Station,
        hebrew: Boolean,
        streamIndex: Int,
        nowPlaying: String?,
        warmed: Boolean,
        skippedRedirect: Boolean,
    ): MediaMetadata {
        val name = station.name(hebrew)
        val frequency = station.primaryFrequency?.let { formatFrequency(it) } ?: ""
        val extras = Bundle().apply {
            putInt(ShidurCommands.EXTRA_STREAM_INDEX, streamIndex)
            putBoolean(ShidurCommands.EXTRA_WARMED, warmed)
            putBoolean(ShidurCommands.EXTRA_SKIPPED_REDIRECT, skippedRedirect)
            if (nowPlaying != null) putString(ShidurCommands.EXTRA_NOW_PLAYING, nowPlaying)
        }
        return MediaMetadata.Builder()
            .setTitle(nowPlaying ?: name)
            .setArtist(if (nowPlaying != null) name else frequency)
            .setDisplayTitle(name)
            .setSubtitle(nowPlaying ?: frequency)
            .setStation(name)
            .setArtworkUri(ArtworkProvider.uriFor(context, station))
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setExtras(extras)
            .build()
    }

    fun folder(id: String, title: String, gridItems: Boolean = false): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                if (gridItems) {
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                } else {
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                },
            )
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
            .setExtras(extras)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    /** A bare item carrying only a station id; the session resolves it to a playable item. */
    fun request(stationId: String): MediaItem = MediaItem.Builder().setMediaId(stationId).build()
}
