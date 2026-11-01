package io.github.eyalshimshi.shidur.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.theme.LocalAnimationsEnabled
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

object PlayerSheetDefaults {
    val MiniHeight: Dp = 64.dp
    val MiniMargin: Dp = 8.dp
    val MiniCorner: Dp = 16.dp

    /** Room the collapsed mini player takes above the navigation bar, margins included. */
    val MiniSpace: Dp = MiniHeight + MiniMargin
}

/** Where the player sheet is: [progress] 0 is the mini player, 1 is the full-screen player. */
@Stable
class PlayerSheetState(initiallyExpanded: Boolean) {

    var progress by mutableFloatStateOf(if (initiallyExpanded) 1f else 0f)
        private set

    /** The state the sheet rests in or is settling to. */
    var isExpanded by mutableStateOf(initiallyExpanded)
        private set

    /** Pixels the sheet's top edge travels between the two states; set by the layout. */
    internal var travelPx = 1f

    /** Lets a newer drag or animation silence an older animation. */
    private var animationId = 0

    suspend fun expand() = animateTo(expanded = true)

    suspend fun collapse() = animateTo(expanded = false)

    fun snapTo(expanded: Boolean) {
        animationId++
        isExpanded = expanded
        progress = if (expanded) 1f else 0f
    }

    internal suspend fun animateTo(expanded: Boolean, velocity: Float = 0f) {
        isExpanded = expanded
        val id = ++animationId
        animate(
            initialValue = progress,
            targetValue = if (expanded) 1f else 0f,
            initialVelocity = velocity,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
            if (id == animationId) progress = value.coerceIn(0f, 1f)
        }
    }

    /** Moves the sheet by a drag of [deltaPx] (positive is down); returns the pixels used. */
    internal fun dragBy(deltaPx: Float): Float {
        if (deltaPx == 0f) return 0f
        animationId++
        val before = progress
        progress = (before - deltaPx / travelPx).coerceIn(0f, 1f)
        return (before - progress) * travelPx
    }

    /** True while the sheet rests neither fully open nor fully closed. */
    internal val isBetweenStates: Boolean
        get() = progress > 0f && progress < 1f

    /** Settles after a drag; [velocityPx] is in pixels per second, positive is down. */
    internal suspend fun settle(velocityPx: Float) {
        val expand = when {
            velocityPx < -FLING_VELOCITY_PX -> true
            velocityPx > FLING_VELOCITY_PX -> false
            else -> progress > 0.5f
        }
        animateTo(expand, velocity = -velocityPx / travelPx)
    }

    companion object {
        private const val FLING_VELOCITY_PX = 1_200f

        val Saver: Saver<PlayerSheetState, Boolean> = Saver(
            save = { it.isExpanded },
            restore = { PlayerSheetState(it) },
        )
    }
}

/**
 * The player as one sheet: a mini player above the navigation bar that can be dragged (or
 * tapped) open into the full player, and dragged, swiped back or tapped closed again.
 */
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    bottomInset: Dp,
    miniColor: Color,
    miniContentColor: Color,
    fullColor: Color,
    fullContentColor: Color,
    modifier: Modifier = Modifier,
    mini: @Composable () -> Unit,
    full: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var backProgress by remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = state.isExpanded) { events ->
        try {
            events.collect { event -> backProgress = event.progress }
            state.collapse()
        } finally {
            backProgress = 0f
        }
    }

    val nestedScroll = remember(state) { SheetNestedScroll(state) }
    val dragState = rememberDraggableState { delta -> state.dragBy(delta) }
    // The full player stays composed while it is the resting state, so a drag inside it that
    // reaches the bottom is not cut off by the layer disappearing under the finger. The mini
    // player leaves once it has faded out, so it never catches taps meant for the full player.
    val showFull by remember(state) { derivedStateOf { state.progress > 0f || state.isExpanded } }
    val showMini by remember(state) { derivedStateOf { state.progress < MINI_FADE_END } }
    val openLabel = stringResource(R.string.action_open_player)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val areaWidth = constraints.maxWidth.toFloat()
        val areaHeight = constraints.maxHeight.toFloat()
        // Read here: the scope's maxWidth/maxHeight aren't reachable from the nested Box below.
        val fullWidth = maxWidth
        val fullHeight = maxHeight
        val margin = with(density) { PlayerSheetDefaults.MiniMargin.toPx() }
        val miniHeight = with(density) { PlayerSheetDefaults.MiniHeight.toPx() }
        val corner = with(density) { PlayerSheetDefaults.MiniCorner.toPx() }
        val backCorner = with(density) { BACK_CORNER.toPx() }
        val elevation = with(density) { MINI_ELEVATION.toPx() }
        val collapsedBottom = areaHeight - with(density) { bottomInset.toPx() } - margin / 2f
        val collapsedTop = (collapsedBottom - miniHeight).coerceAtLeast(0f)
        state.travelPx = collapsedTop.coerceAtLeast(1f)

        Box(
            modifier = Modifier
                .offset {
                    val p = state.progress
                    IntOffset((margin * (1f - p)).roundToInt(), (collapsedTop * (1f - p)).roundToInt())
                }
                .layout { measurable, _ ->
                    val p = state.progress
                    val sheetWidth = (areaWidth - 2f * margin * (1f - p)).roundToInt().coerceAtLeast(0)
                    val top = collapsedTop * (1f - p)
                    val bottom = collapsedBottom + (areaHeight - collapsedBottom) * p
                    val sheetHeight = (bottom - top).roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(sheetWidth, sheetHeight))
                    layout(sheetWidth, sheetHeight) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val p = state.progress
                    // Predictive back shrinks the open player toward the mini player.
                    val squash = 1f - BACK_SCALE * backProgress * p
                    scaleX = squash
                    scaleY = squash
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    shape = RoundedCornerShape(corner * (1f - p) + backCorner * backProgress * p)
                    clip = true
                    shadowElevation = elevation * (1f - p)
                }
                .drawBehind { drawRect(lerp(miniColor, fullColor, state.progress)) }
                .nestedScroll(nestedScroll)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> state.settle(velocity) },
                ),
        ) {
            if (showFull) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopCenter, unbounded = true)
                        .requiredSize(fullWidth, fullHeight)
                        .graphicsLayer { alpha = ((state.progress - 0.1f) / 0.5f).coerceIn(0f, 1f) },
                ) {
                    CompositionLocalProvider(LocalContentColor provides fullContentColor) { full() }
                }
            }
            if (showMini) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(PlayerSheetDefaults.MiniHeight)
                        .graphicsLayer { alpha = (1f - state.progress / MINI_FADE_END).coerceIn(0f, 1f) }
                        .clickable(onClickLabel = openLabel) { scope.launch { state.expand() } },
                ) {
                    CompositionLocalProvider(LocalContentColor provides miniContentColor) { mini() }
                }
            }
        }
    }
}

/** Lets vertical drags inside the full player move the sheet when its content can't scroll. */
private class SheetNestedScroll(private val state: PlayerSheetState) : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // While the sheet is partly closed, vertical drags move the sheet before the content.
        if (source != NestedScrollSource.UserInput || available.y == 0f || state.progress >= 1f) {
            return Offset.Zero
        }
        return Offset(0f, state.dragBy(available.y))
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // The content is at its top and the finger keeps moving down: close the sheet.
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        return Offset(0f, state.dragBy(available.y))
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // Only a sheet left between its states takes the fling, and only its vertical part, so
        // sideways flings still change station.
        if (!state.isBetweenStates) return Velocity.Zero
        state.settle(available.y)
        return Velocity(0f, available.y)
    }
}

/** The collapsed player: swipe sideways to change station, tap to open. */
@Composable
fun MiniPlayer(
    station: Station,
    queue: PlayerQueue,
    hebrew: Boolean,
    player: PlayerUiState,
    onSelectStation: (Int) -> Unit,
    onTogglePlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StationPager(
            queue = queue,
            onSelect = onSelectStation,
            modifier = Modifier.weight(1f),
        ) { pageStation ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                StationArtwork(pageStation, 44.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = pageStation.name(hebrew),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val status = if (pageStation.id == station.id) playerStatusText(player) else null
                    Text(
                        text = status ?: stationSubtitle(pageStation),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        PlayPauseButton(player = player, onClick = onTogglePlay)
    }
}

/** The stations in the player's queue and the position of the current one. */
data class PlayerQueue(val stations: List<Station>, val index: Int) {
    companion object {
        /** The session's queue, or just [current] if some queued station is unknown. */
        fun of(current: Station, player: PlayerUiState, stationsById: Map<String, Station>): PlayerQueue {
            val stations = player.queueIds.map { stationsById[it] }
            if (player.currentIndex !in stations.indices || stations.any { it == null }) {
                return PlayerQueue(listOf(current), 0)
            }
            return PlayerQueue(stations.filterNotNull(), player.currentIndex)
        }
    }
}

/**
 * A horizontal pager over the queue that follows the current station and reports the user's
 * swipes to [onSelect].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationPager(
    queue: PlayerQueue,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    page: @Composable (Station) -> Unit,
) {
    val stations = queue.stations
    val pagerState = rememberPagerState(initialPage = queue.index) { stations.size }
    val animations = LocalAnimationsEnabled.current
    val currentIndex by rememberUpdatedState(queue.index)
    val select by rememberUpdatedState(onSelect)
    val swipe = remember { SwipeTracker() }
    if (swipe.queue != queue) {
        // A new queue or position from the player: whatever the pager does next (following it,
        // or clamping to a shorter queue) is not the user's swipe.
        swipe.queue = queue
        swipe.userSwiped = false
    }

    LaunchedEffect(queue) {
        val target = queue.index
        if (target !in stations.indices || pagerState.currentPage == target || pagerState.isScrollInProgress) {
            return@LaunchedEffect
        }
        if (animations && abs(pagerState.currentPage - target) == 1) {
            pagerState.animateScrollToPage(target)
        } else {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState) {
        launch {
            pagerState.interactionSource.interactions.collect { interaction ->
                if (interaction is DragInteraction.Start) swipe.userSwiped = true
            }
        }
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (swipe.userSwiped) {
                swipe.userSwiped = false
                if (settled != currentIndex) select(settled)
            }
        }
    }
    HorizontalPager(state = pagerState, modifier = modifier) { index ->
        stations.getOrNull(index)?.let { page(it) }
    }
}

/** Remembers whether the pager's latest movement came from the user's finger. */
private class SwipeTracker {
    var queue: PlayerQueue? = null
    var userSwiped = false
}

private const val BACK_SCALE = 0.1f
private val BACK_CORNER = 28.dp
private val MINI_ELEVATION = 3.dp

/** Sheet progress at which the mini player has fully faded out. */
private const val MINI_FADE_END = 1f / 3f
