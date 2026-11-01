package io.github.eyalshimshi.shidur.ui.screens

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.cast.MediaRouteButton
import androidx.media3.common.util.UnstableApi
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.model.formatFrequency
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.playback.StartRecord
import io.github.eyalshimshi.shidur.ui.LibraryUiState
import io.github.eyalshimshi.shidur.ui.components.FavoriteButton
import io.github.eyalshimshi.shidur.ui.components.PlayerQueue
import io.github.eyalshimshi.shidur.ui.components.StationArtwork
import io.github.eyalshimshi.shidur.ui.components.StationPager
import io.github.eyalshimshi.shidur.ui.components.playerStatusText
import io.github.eyalshimshi.shidur.ui.components.secondsText
import io.github.eyalshimshi.shidur.ui.components.stationSubtitle
import io.github.eyalshimshi.shidur.ui.copyToClipboard
import io.github.eyalshimshi.shidur.ui.reportProblemUrl
import io.github.eyalshimshi.shidur.ui.searchSong
import io.github.eyalshimshi.shidur.ui.shareStation
import io.github.eyalshimshi.shidur.ui.theme.LocalAnimationsEnabled
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The full-screen player, shown inside the player sheet. Colors come from the sheet's theme (the
 * station's colors when enabled).
 */
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayer(
    station: Station,
    queue: PlayerQueue,
    library: LibraryUiState,
    player: PlayerUiState,
    lastStart: StartRecord?,
    backgroundGradient: Boolean,
    hebrew: Boolean,
    castAvailable: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectStation: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onSetSleepTimer: (Int) -> Unit,
    onExtendSleepTimer: (Long) -> Unit,
    onCancelSleepTimer: () -> Unit,
) {
    var showSleepTimer by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme

    Box(modifier = Modifier.fillMaxSize()) {
        // A glow of the station's color behind the artwork. Pure black backgrounds stay black.
        if (backgroundGradient && scheme.background != Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .background(
                        Brush.verticalGradient(listOf(scheme.primaryContainer.copy(alpha = 0.85f), Color.Transparent)),
                    ),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = stringResource(R.string.action_close_player))
                }
                Spacer(Modifier.weight(1f))
                if (castAvailable) CastButton()
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.action_station_info))
                }
            }
            PlayerContent(
                station = station,
                queue = queue,
                library = library,
                player = player,
                lastStart = lastStart,
                hebrew = hebrew,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSelectStation = onSelectStation,
                onToggleFavorite = onToggleFavorite,
                onOpenSleepTimer = { showSleepTimer = true },
            )
        }
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            endElapsedRealtime = player.sleepTimerEnd,
            onDismiss = { showSleepTimer = false },
            onSet = { minutes ->
                onSetSleepTimer(minutes)
                showSleepTimer = false
            },
            onExtend = { player.sleepTimerEnd?.let(onExtendSleepTimer) },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimer = false
            },
        )
    }
    if (showInfo) {
        StationInfoSheet(station = station, hebrew = hebrew, onDismiss = { showInfo = false })
    }
}

@Composable
private fun PlayerContent(
    station: Station,
    queue: PlayerQueue,
    library: LibraryUiState,
    player: PlayerUiState,
    lastStart: StartRecord?,
    hebrew: Boolean,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSelectStation: (Int) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSleepTimer: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val configuration = LocalConfiguration.current
    val animations = LocalAnimationsEnabled.current
    val artworkSize = minOf(configuration.screenWidthDp * 0.7f, configuration.screenHeightDp * 0.38f, 300f).dp
    val artworkScale by animateFloatAsState(
        targetValue = if (player.isPlaying || !animations) 1f else 0.92f,
        label = "artwork-scale",
    )
    var songMenu by remember { mutableStateOf(false) }

    Spacer(Modifier.height(16.dp))
    // Swipe the artwork to change station.
    StationPager(
        queue = queue,
        onSelect = onSelectStation,
        modifier = Modifier
            .fillMaxWidth()
            .height(artworkSize),
    ) { pageStation ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StationArtwork(
                station = pageStation,
                size = artworkSize,
                shape = RoundedCornerShape(artworkSize * 0.22f),
                modifier = Modifier.graphicsLayer {
                    scaleX = artworkScale
                    scaleY = artworkScale
                },
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Text(
        text = station.name(hebrew),
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stationSubtitle(station),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (player.isPlaying) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "● " + stringResource(R.string.player_live),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    val status = playerStatusText(player)
    Box {
        Text(
            text = status ?: " ",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = player.nowPlaying != null) { songMenu = true }
                .padding(8.dp),
        )
        DropdownMenu(expanded = songMenu, onDismissRequest = { songMenu = false }) {
            val song = player.nowPlaying.orEmpty()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_copy)) },
                onClick = {
                    copyToClipboard(context, song)
                    songMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_search)) },
                onClick = {
                    searchSong(context, song)
                    songMenu = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_share)) },
                onClick = {
                    shareStation(context, station, hebrew, song)
                    songMenu = false
                },
            )
        }
    }
    if (lastStart != null) {
        val startedIn = stringResource(R.string.player_started_in, secondsText(lastStart.millis))
        Text(
            text = if (lastStart.instant) startedIn + " · " + stringResource(R.string.perf_instant) else startedIn,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))

    val playShapeSpec: AnimationSpec<Dp> = if (animations) spring() else snap()
    val playCorner by animateDpAsState(
        targetValue = if (player.playWhenReady) 28.dp else 44.dp,
        animationSpec = playShapeSpec,
        label = "play-shape",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(
            onClick = onPrevious,
            enabled = player.queueIds.size > 1,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.action_previous_station),
                modifier = Modifier.size(32.dp),
            )
        }
        FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(playCorner),
        ) {
            when {
                player.hasError -> Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_retry),
                    modifier = Modifier.size(40.dp),
                )
                player.isBuffering && player.playWhenReady -> CircularProgressIndicator(
                    color = LocalContentColor.current,
                    modifier = Modifier.size(36.dp),
                )
                player.playWhenReady -> Icon(
                    Icons.Rounded.Pause,
                    contentDescription = stringResource(R.string.action_pause),
                    modifier = Modifier.size(40.dp),
                )
                else -> Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.action_play),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        IconButton(
            onClick = onNext,
            enabled = player.queueIds.size > 1,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.action_next_station),
                modifier = Modifier.size(32.dp),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FavoriteButton(
            isFavorite = station.id in library.favoriteIds,
            onToggle = { onToggleFavorite(station.id) },
        )
        IconButton(onClick = onOpenSleepTimer) {
            Icon(Icons.Rounded.Bedtime, contentDescription = stringResource(R.string.sleep_timer_title))
        }
        IconButton(onClick = { shareStation(context, station, hebrew) }) {
            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.action_share))
        }
    }
    val remaining = rememberRemaining(player.sleepTimerEnd)
    if (remaining != null) {
        Text(
            text = stringResource(R.string.sleep_stops_in, formatRemaining(remaining)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (player.hasError) {
        Spacer(Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.player_error),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            )
            Row(modifier = Modifier.padding(8.dp)) {
                TextButton(onClick = onTogglePlay) { Text(stringResource(R.string.action_retry)) }
                TextButton(onClick = { uriHandler.openUri(reportProblemUrl(station)) }) {
                    Text(stringResource(R.string.action_report))
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}

@OptIn(UnstableApi::class)
@Composable
private fun CastButton() {
    MediaRouteButton()
}

@kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerSheet(
    endElapsedRealtime: Long?,
    onDismiss: () -> Unit,
    onSet: (Int) -> Unit,
    onExtend: () -> Unit,
    onCancel: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(stringResource(R.string.sleep_timer_title), style = MaterialTheme.typography.titleLarge)
            val remaining = rememberRemaining(endElapsedRealtime)
            if (remaining != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.sleep_stops_in, formatRemaining(remaining)),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onExtend) { Text(stringResource(R.string.sleep_extend)) }
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.sleep_cancel)) }
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SLEEP_PRESETS.forEach { minutes ->
                    AssistChip(
                        onClick = { onSet(minutes) },
                        label = { Text(stringResource(R.string.sleep_minutes, minutes)) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationInfoSheet(station: Station, hebrew: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationArtwork(station, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(station.name(hebrew), style = MaterialTheme.typography.titleLarge)
                    Text(
                        station.name(!hebrew),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (station.frequencies.isNotEmpty()) {
                Text(stringResource(R.string.info_frequencies), style = MaterialTheme.typography.labelLarge)
                Text(
                    station.frequencies.joinToString(" · ") { formatFrequency(it) },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
            }
            station.website?.let { website ->
                TextButton(onClick = { uriHandler.openUri(website) }) {
                    Text(stringResource(R.string.info_website))
                }
            }
            station.streams.firstOrNull()?.let { stream ->
                TextButton(onClick = { copyToClipboard(context, stream.url) }) {
                    Text(stringResource(R.string.info_copy_stream))
                }
            }
            TextButton(onClick = { uriHandler.openUri(reportProblemUrl(station)) }) {
                Text(stringResource(R.string.action_report))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.info_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Remaining sleep-timer time in milliseconds, ticking every second. */
@Composable
private fun rememberRemaining(endElapsedRealtime: Long?): Long? {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(endElapsedRealtime) {
        while (endElapsedRealtime != null) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    return endElapsedRealtime?.let { (it - now).coerceAtLeast(0L) }
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

private val SLEEP_PRESETS = listOf(15, 30, 45, 60, 90, 120)
