package io.github.eyalshimshi.shidur.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.formatFrequency
import io.github.eyalshimshi.shidur.playback.MonogramRenderer
import io.github.eyalshimshi.shidur.playback.PlayerUiState

/** The station's "logo": its short name on its brand color. */
@Composable
fun StationArtwork(
    station: Station,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(size * 0.28f),
) {
    val background = Color(station.color)
    val content = if (background.luminance() > 0.5f) Color(0xFF1D1B20) else Color.White
    val fontSize = with(LocalDensity.current) { (size * MonogramRenderer.fontScaleFor(station.shortName)).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = station.shortName,
            color = content,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/** "88.0 FM", "FM · varies by region", "Custom station" or "Digital". */
@Composable
fun stationSubtitle(station: Station): String = when {
    station.primaryFrequency != null -> formatFrequency(station.primaryFrequency)
    station.isCustom -> stringResource(R.string.custom_station)
    station.frequencies.isNotEmpty() -> stringResource(R.string.fm_varies)
    else -> stringResource(R.string.digital_station)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationRow(
    station: Station,
    hebrew: Boolean,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        headlineContent = {
            Text(station.name(hebrew), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(stationSubtitle(station), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { StationArtwork(station, 48.dp) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent && isPlaying) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing_indicator),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                FavoriteButton(isFavorite = isFavorite, onToggle = onToggleFavorite)
            }
        },
        colors = if (isCurrent) {
            ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        } else {
            ListItemDefaults.colors()
        },
    )
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onToggle, modifier = modifier) {
        Icon(
            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
        )
    }
}

@Composable
fun StationTile(
    station: Station,
    hebrew: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkSize: Dp = 72.dp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            StationArtwork(station, artworkSize)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing_indicator),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = station.name(hebrew),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(artworkSize + 12.dp),
        )
    }
}

/** Play/pause that shows progress while connecting and a retry icon after an error. */
@Composable
fun PlayPauseButton(player: PlayerUiState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        when {
            player.hasError -> Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_retry))
            player.isBuffering && player.playWhenReady -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.5.dp,
            )
            player.playWhenReady -> Icon(Icons.Rounded.Pause, contentDescription = stringResource(R.string.action_pause))
            else -> Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.action_play))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    station: Station,
    hebrew: Boolean,
    player: PlayerUiState,
    onOpen: () -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onOpen,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StationArtwork(station, 44.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = station.name(hebrew),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = playerStatusText(player) ?: stationSubtitle(station),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PlayPauseButton(player = player, onClick = onTogglePlay)
        }
    }
}

/** Status line for the player: error, connecting, or the song title when known. */
@Composable
fun playerStatusText(player: PlayerUiState): String? = when {
    player.hasError -> stringResource(R.string.player_error_short)
    player.isBuffering && player.playWhenReady -> stringResource(R.string.player_connecting)
    else -> player.nowPlaying
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
}
