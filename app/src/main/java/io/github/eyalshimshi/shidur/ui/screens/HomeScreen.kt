package io.github.eyalshimshi.shidur.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.LibraryUiState
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.StationTile

/**
 * Home as shelves (inspired by Echo): favorites, recently played and one row per category, each
 * scrolling sideways. A shelf's title opens its full list.
 */
@Composable
fun HomeScreen(
    library: LibraryUiState,
    player: PlayerUiState,
    hebrew: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCollection: (String?) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    val shelves = remember(library.stations, library.collections) {
        library.collections.sortedBy { it.order }.mapNotNull { collection ->
            val stations = library.stations
                .filter { !it.isCustom && collection.id in it.collections }
                .sortedBy { it.rank }
            if (stations.isEmpty()) null else collection to stations
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomContentPadding.current),
    ) {
        item(key = "search") { SearchBar(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings) }

        item(key = "favorites-title") {
            ShelfTitle(
                title = stringResource(R.string.section_favorites),
                onClick = if (library.favorites.isEmpty()) null else onOpenFavorites,
            )
        }
        if (library.favorites.isEmpty()) {
            item(key = "favorites-empty") { EmptyFavoritesCard(onBrowse = { onOpenCollection(null) }) }
        } else {
            item(key = "favorites") { StationShelf(library.favorites, player, hebrew, onPlay) }
        }

        if (library.recents.isNotEmpty()) {
            item(key = "recent-title") { ShelfTitle(title = stringResource(R.string.section_recent), onClick = null) }
            item(key = "recent") { StationShelf(library.recents, player, hebrew, onPlay) }
        }

        shelves.forEach { (collection, stations) ->
            item(key = "title-" + collection.id) {
                ShelfTitle(title = collection.name(hebrew), onClick = { onOpenCollection(collection.id) })
            }
            item(key = "shelf-" + collection.id) { StationShelf(stations, player, hebrew, onPlay) }
        }

        item(key = "all") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                FilledTonalButton(onClick = { onOpenCollection(null) }) {
                    Text(stringResource(R.string.home_all_stations))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.weight(1f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Search, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(AppIcons.Settings, contentDescription = stringResource(R.string.settings_title))
        }
    }
}

/** A shelf's title; with [onClick] it shows an arrow and opens the full list. */
@Composable
private fun ShelfTitle(title: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (onClick != null) {
            Spacer(Modifier.width(4.dp))
            Icon(AppIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(22.dp))
        }
    }
}

/** A sideways-scrolling row of big station cards; playing one queues the whole row. */
@Composable
private fun StationShelf(
    stations: List<Station>,
    player: PlayerUiState,
    hebrew: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(stations, key = { it.id }) { station ->
            StationTile(
                station = station,
                hebrew = hebrew,
                isCurrent = station.id == player.stationId,
                onClick = { onPlay(station, stations) },
                artworkSize = SHELF_ARTWORK_SIZE,
            )
        }
    }
}

@Composable
private fun EmptyFavoritesCard(onBrowse: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Text(
            text = stringResource(R.string.favorites_empty_body),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onBrowse,
            modifier = Modifier.padding(16.dp),
        ) {
            Text(stringResource(R.string.action_browse_stations))
        }
    }
}

private val SHELF_ARTWORK_SIZE = 112.dp
