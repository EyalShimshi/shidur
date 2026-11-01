package io.github.eyalshimshi.shidur.playback

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.SearchEngine
import io.github.eyalshimshi.shidur.model.Station
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch

/** Browse tree for Android Auto and other controllers, search, custom commands and resumption. */
@OptIn(UnstableApi::class)
class LibrarySessionCallback(
    private val service: PlaybackService,
) : MediaLibraryService.MediaLibrarySession.Callback {

    private val scope get() = service.serviceScope

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(ShidurCommands.command(ShidurCommands.TOGGLE_FAVORITE))
            .add(ShidurCommands.command(ShidurCommands.SET_SLEEP_TIMER))
            .add(ShidurCommands.command(ShidurCommands.CANCEL_SLEEP_TIMER))
            .add(ShidurCommands.command(ShidurCommands.RETRY))
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setMediaButtonPreferences(service.mediaButtonPreferences())
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        when (customCommand.customAction) {
            ShidurCommands.TOGGLE_FAVORITE -> service.toggleCurrentFavorite()
            ShidurCommands.SET_SLEEP_TIMER -> service.startSleepTimer(
                args.getLong(ShidurCommands.ARG_DURATION_MS),
                args.getBoolean(ShidurCommands.ARG_FADE_OUT, true),
            )
            ShidurCommands.CANCEL_SLEEP_TIMER -> service.cancelSleepTimer()
            ShidurCommands.RETRY -> service.retryCurrentStation()
            else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    override fun onGetLibraryRoot(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val root = MediaItemFactory.folder(MediaItemFactory.ROOT_ID, service.getString(R.string.app_name))
        val rootExtras = Bundle().apply { putBoolean(SEARCH_SUPPORTED_KEY, true) }
        val rootParams = MediaLibraryService.LibraryParams.Builder().setExtras(rootExtras).build()
        return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
    }

    override fun onGetChildren(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        LibraryResult.ofItemList(childrenOf(parentId), params)
    }

    override fun onGetItem(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String,
    ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
        val stationId = MediaItemFactory.parseId(mediaId).second
        val station = service.awaitStations().firstOrNull { it.id == stationId }
        if (station != null) {
            LibraryResult.ofItem(service.playableItem(station, mediaId), null)
        } else {
            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }
    }

    override fun onSearch(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<Void>> {
        scope.launch {
            val count = SearchEngine.search(query, service.awaitStations()).size
            session.notifySearchResultChanged(browser, query, count, params)
        }
        return Futures.immediateFuture(LibraryResult.ofVoid())
    }

    override fun onGetSearchResult(
        session: MediaLibraryService.MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: MediaLibraryService.LibraryParams?,
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
        val results = SearchEngine.search(query, service.awaitStations())
        LibraryResult.ofItemList(results.map { service.playableItem(it) }, params)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
    ): ListenableFuture<List<MediaItem>> = scope.future { resolve(mediaItems) }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val requested = mediaItems.getOrNull(if (startIndex == C.INDEX_UNSET) 0 else startIndex) ?: mediaItems.firstOrNull()
        requested?.let { service.awaitWarmUp(MediaItemFactory.parseId(it.mediaId).second) }
        if (mediaItems.size == 1) {
            expandSingleItem(mediaItems[0])?.let { return@future it }
        }
        val resolved = resolve(mediaItems)
        val index = if (startIndex == C.INDEX_UNSET) 0 else startIndex.coerceIn(0, maxOf(0, resolved.lastIndex))
        MediaSession.MediaItemsWithStartPosition(resolved, index, C.TIME_UNSET)
    }

    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        isForPlayback: Boolean,
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
        val last = service.container.libraryRepository.lastPlayed() ?: error("Nothing to resume")
        val byId = service.awaitStations().associateBy { it.id }
        val queue = last.queue.mapNotNull { byId[it] }.ifEmpty { listOfNotNull(byId[last.stationId]) }
        if (queue.isEmpty()) error("The last station is no longer available")
        val index = queue.indexOfFirst { it.id == last.stationId }.coerceAtLeast(0)
        MediaSession.MediaItemsWithStartPosition(queue.map { service.playableItem(it) }, index, C.TIME_UNSET)
    }

    private suspend fun childrenOf(parentId: String): List<MediaItem> {
        val hebrew = service.hebrew()
        return when (parentId) {
            MediaItemFactory.ROOT_ID -> listOf(
                MediaItemFactory.folder(
                    MediaItemFactory.FAVORITES_ID,
                    service.getString(R.string.auto_favorites),
                    gridItems = true,
                ),
                MediaItemFactory.folder(MediaItemFactory.RECENTS_ID, service.getString(R.string.auto_recent)),
                MediaItemFactory.folder(MediaItemFactory.CATEGORIES_ID, service.getString(R.string.auto_categories)),
                MediaItemFactory.folder(MediaItemFactory.ALL_ID, service.getString(R.string.auto_all)),
            )
            MediaItemFactory.CATEGORIES_ID -> {
                val collections = service.container.catalogRepository.catalog.value?.collections.orEmpty()
                collections.map { MediaItemFactory.folder(MediaItemFactory.COLLECTION_PREFIX + it.id, it.name(hebrew)) }
            }
            else -> stationsIn(parentId).map { station ->
                service.playableItem(station, MediaItemFactory.browseId(parentId, station.id))
            }
        }
    }

    private suspend fun stationsIn(parentId: String): List<Station> {
        val stations = service.awaitStations()
        val byId = stations.associateBy { it.id }
        val library = service.container.libraryRepository
        return when {
            parentId == MediaItemFactory.FAVORITES_ID -> library.favoriteIds.first().mapNotNull { byId[it] }
            parentId == MediaItemFactory.RECENTS_ID -> library.recentIds.first().mapNotNull { byId[it] }
            parentId == MediaItemFactory.ALL_ID -> stations.filter { !it.isCustom }
            parentId.startsWith(MediaItemFactory.COLLECTION_PREFIX) -> {
                val collectionId = parentId.removePrefix(MediaItemFactory.COLLECTION_PREFIX)
                stations.filter { collectionId in it.collections }
            }
            else -> emptyList()
        }
    }

    /** Resolves bare station ids (from the app, the widget or a car) into playable items. */
    private suspend fun resolve(mediaItems: List<MediaItem>): List<MediaItem> {
        val stations = service.awaitStations()
        val byId = stations.associateBy { it.id }
        return mediaItems.flatMap { item ->
            val station = byId[MediaItemFactory.parseId(item.mediaId).second]
            val query = item.requestMetadata.searchQuery
            when {
                station != null -> listOf(service.playableItem(station))
                query != null -> SearchEngine.search(query, stations).take(1).map { service.playableItem(it) }
                else -> emptyList()
            }
        }
    }

    /**
     * A single item from a browse folder queues the whole folder, so next/previous moves between
     * its stations. A voice request ("play Galgalatz") is resolved through search.
     */
    private suspend fun expandSingleItem(item: MediaItem): MediaSession.MediaItemsWithStartPosition? {
        val (parentId, stationId) = MediaItemFactory.parseId(item.mediaId)
        if (parentId != null) {
            val siblings = stationsIn(parentId)
            val index = siblings.indexOfFirst { it.id == stationId }
            if (index < 0) return null
            return MediaSession.MediaItemsWithStartPosition(
                siblings.map { service.playableItem(it) },
                index,
                C.TIME_UNSET,
            )
        }
        val query = item.requestMetadata.searchQuery
        if (stationId.isNotEmpty() || query == null) return null
        val match = SearchEngine.search(query, service.awaitStations()).firstOrNull() ?: return null
        val favorites = stationsIn(MediaItemFactory.FAVORITES_ID)
        val queue = if (favorites.any { it.id == match.id }) favorites else listOf(match)
        return MediaSession.MediaItemsWithStartPosition(
            queue.map { service.playableItem(it) },
            queue.indexOfFirst { it.id == match.id }.coerceAtLeast(0),
            C.TIME_UNSET,
        )
    }

    private companion object {
        /** Root hint that tells Android Auto the app supports search. */
        const val SEARCH_SUPPORTED_KEY = "android.media.browse.SEARCH_SUPPORTED"
    }
}
