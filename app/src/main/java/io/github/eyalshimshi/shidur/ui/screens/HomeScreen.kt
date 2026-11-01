package io.github.eyalshimshi.shidur.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.LibraryUiState
import io.github.eyalshimshi.shidur.ui.components.GroupShapes
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.SectionHeader
import io.github.eyalshimshi.shidur.ui.components.StationRow
import io.github.eyalshimshi.shidur.ui.components.StationTile
import io.github.eyalshimshi.shidur.ui.components.groupItem

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    library: LibraryUiState,
    player: PlayerUiState,
    hebrew: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCollection: (String?) -> Unit,
    onOpenFavorites: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + LocalBottomContentPadding.current),
    ) {
        item {
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
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings_title))
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.section_favorites),
                action = if (library.favorites.size > HOME_FAVORITES) {
                    { TextButton(onClick = onOpenFavorites) { Text(stringResource(R.string.see_all)) } }
                } else {
                    null
                },
            )
        }
        if (library.favorites.isEmpty()) {
            item {
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
                        onClick = { onOpenCollection(null) },
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text(stringResource(R.string.action_browse_stations))
                    }
                }
            }
        } else {
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    library.favorites.take(HOME_FAVORITES).forEach { station ->
                        StationTile(
                            station = station,
                            hebrew = hebrew,
                            isCurrent = station.id == player.stationId,
                            onClick = { onPlay(station, library.favorites) },
                        )
                    }
                }
            }
        }

        if (library.recents.isNotEmpty()) {
            item { SectionHeader(title = stringResource(R.string.section_recent)) }
            val recents = library.recents.take(HOME_RECENTS)
            itemsIndexed(recents, key = { _, station -> "recent-" + station.id }) { index, station ->
                StationRow(
                    station = station,
                    hebrew = hebrew,
                    isFavorite = station.id in library.favoriteIds,
                    isCurrent = station.id == player.stationId,
                    isPlaying = player.isPlaying,
                    onClick = { onPlay(station, library.recents) },
                    onToggleFavorite = { onToggleFavorite(station.id) },
                    modifier = Modifier
                        .padding(bottom = if (index < recents.lastIndex) GroupShapes.Gap else 0.dp)
                        .groupItem(index, recents.size, MaterialTheme.colorScheme.surfaceContainer),
                )
            }
        }

        item { SectionHeader(title = stringResource(R.string.section_browse)) }
        item {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { onOpenCollection(null) },
                    label = { Text(stringResource(R.string.collection_all)) },
                )
                library.collections.forEach { collection ->
                    AssistChip(
                        onClick = { onOpenCollection(collection.id) },
                        label = { Text(collection.name(hebrew)) },
                    )
                }
            }
        }
    }
}

private const val HOME_FAVORITES = 8
private const val HOME_RECENTS = 5
