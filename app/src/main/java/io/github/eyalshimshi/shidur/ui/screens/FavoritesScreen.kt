package io.github.eyalshimshi.shidur.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.LibraryUiState
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.StationTile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    library: LibraryUiState,
    player: PlayerUiState,
    hebrew: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (Station) -> Unit,
    onAddByUrl: () -> Unit,
    onBrowse: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.nav_favorites)) },
            actions = {
                if (library.favorites.isNotEmpty()) {
                    TextButton(onClick = { editing = !editing }) {
                        Text(stringResource(if (editing) R.string.action_done else R.string.action_edit))
                    }
                }
                IconButton(onClick = onAddByUrl) {
                    Icon(AppIcons.Add, contentDescription = stringResource(R.string.action_add_station))
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )
        if (library.favorites.isEmpty()) {
            EmptyFavorites(onBrowse = onBrowse)
        } else {
            ReorderableFavoritesGrid(
                favorites = library.favorites,
                currentStationId = player.stationId,
                hebrew = hebrew,
                editing = editing,
                onPlay = onPlay,
                onReorder = onReorder,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun ReorderableFavoritesGrid(
    favorites: List<Station>,
    currentStationId: String?,
    hebrew: Boolean,
    editing: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (Station) -> Unit,
) {
    var ordered by remember(favorites) { mutableStateOf(favorites) }
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val reorderState = rememberReorderableLazyGridState(gridState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    fun move(from: Int, to: Int) {
        if (from !in ordered.indices || to !in ordered.indices) return
        val updated = ordered.toMutableList().apply { add(to, removeAt(from)) }
        ordered = updated
        onReorder(updated.map { it.id })
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 96.dp),
        state = gridState,
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp + LocalBottomContentPadding.current,
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(ordered, key = { it.id }) { station ->
            ReorderableItem(reorderState, key = station.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag-elevation")
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = elevation,
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        StationTile(
                            station = station,
                            hebrew = hebrew,
                            isCurrent = station.id == currentStationId,
                            onClick = { onPlay(station, ordered) },
                            modifier = Modifier
                                .longPressDraggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { onReorder(ordered.map { it.id }) },
                                )
                                .semantics {
                                    val index = ordered.indexOfFirst { it.id == station.id }
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUpLabel) {
                                            move(index, index - 1)
                                            true
                                        },
                                        CustomAccessibilityAction(moveDownLabel) {
                                            move(index, index + 1)
                                            true
                                        },
                                    )
                                },
                        )
                        if (editing) {
                            FilledTonalIconButton(
                                onClick = { onRemove(station) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    AppIcons.Close,
                                    contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFavorites(onBrowse: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            AppIcons.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.favorites_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.favorites_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBrowse) { Text(stringResource(R.string.action_browse_stations)) }
    }
}
