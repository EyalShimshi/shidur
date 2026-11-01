package io.github.eyalshimshi.shidur.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.LibraryUiState
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.GroupShapes
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.StationRow
import io.github.eyalshimshi.shidur.ui.components.groupItem
import java.text.Collator
import java.util.Locale

enum class StationSort { RECOMMENDED, NAME, FREQUENCY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    library: LibraryUiState,
    player: PlayerUiState,
    hebrew: Boolean,
    initialCollection: String?,
    onPlay: (Station, List<Station>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(initialCollection) }
    var sort by rememberSaveable { mutableStateOf(StationSort.RECOMMENDED) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val stations = remember(library.stations, selected, sort, hebrew) {
        val collectionId = selected
        val filtered = library.stations.filter { station ->
            !station.isCustom && (collectionId == null || collectionId in station.collections)
        }
        sortStations(filtered, sort, hebrew)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_browse)) },
            actions = {
                IconButton(onClick = onOpenSearch) {
                    Icon(AppIcons.Search, contentDescription = stringResource(R.string.search_hint))
                }
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(AppIcons.Sort, contentDescription = stringResource(R.string.action_sort))
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        StationSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                StationSort.RECOMMENDED -> R.string.sort_recommended
                                                StationSort.NAME -> R.string.sort_name
                                                StationSort.FREQUENCY -> R.string.sort_frequency
                                            },
                                        ),
                                    )
                                },
                                onClick = {
                                    sort = option
                                    sortMenuOpen = false
                                },
                            )
                        }
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = selected == null,
                    onClick = { selected = null },
                    label = { Text(stringResource(R.string.collection_all)) },
                )
            }
            items(library.collections, key = { it.id }) { collection ->
                FilterChip(
                    selected = selected == collection.id,
                    onClick = { selected = collection.id },
                    label = { Text(collection.name(hebrew)) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + LocalBottomContentPadding.current),
        ) {
            itemsIndexed(stations, key = { _, station -> station.id }) { index, station ->
                StationRow(
                    station = station,
                    hebrew = hebrew,
                    isFavorite = station.id in library.favoriteIds,
                    isCurrent = station.id == player.stationId,
                    isPlaying = player.isPlaying,
                    onClick = { onPlay(station, stations) },
                    onToggleFavorite = { onToggleFavorite(station.id) },
                    modifier = Modifier
                        .padding(bottom = if (index < stations.lastIndex) GroupShapes.Gap else 0.dp)
                        .groupItem(index, stations.size, MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }
    }
}

private fun sortStations(stations: List<Station>, sort: StationSort, hebrew: Boolean): List<Station> = when (sort) {
    StationSort.RECOMMENDED -> stations.sortedBy { it.rank }
    StationSort.NAME -> {
        val collator = Collator.getInstance(if (hebrew) Locale.forLanguageTag("he") else Locale.ENGLISH)
        stations.sortedWith { a, b -> collator.compare(a.name(hebrew), b.name(hebrew)) }
    }
    StationSort.FREQUENCY -> stations.sortedBy { it.primaryFrequency ?: it.frequencies.minOrNull() ?: Double.MAX_VALUE }
}
