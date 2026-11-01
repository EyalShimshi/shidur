package io.github.eyalshimshi.shidur.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.eyalshimshi.shidur.MainActivity
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.ShidurApplication
import io.github.eyalshimshi.shidur.data.isHebrewUi
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.MonogramRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class WidgetPlaybackState(
    val stationId: String? = null,
    val stationName: String? = null,
    val nowPlaying: String? = null,
    val isPlaying: Boolean = false,
)

/** Latest playback state, written by the playback service and read by the widget. */
object WidgetStateHolder {
    val state = MutableStateFlow(WidgetPlaybackState())
}

private val OpenPlayerKey = ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_PLAYER)

class ShidurWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as ShidurApplication).container
        val hebrew = isHebrewUi(context)
        val stations = container.catalogRepository.awaitStations()
        val byId = stations.associateBy { it.id }
        val favorites = container.libraryRepository.favoriteIds.first().mapNotNull { byId[it] }.take(MAX_FAVORITES)
        val lastStationId = container.libraryRepository.lastPlayed()?.stationId
        val bitmapCache = HashMap<String, Bitmap>()
        val bitmapFor: (Station) -> Bitmap = { station ->
            bitmapCache.getOrPut(station.id) { MonogramRenderer.render(station, ARTWORK_PX) }
        }

        provideContent {
            val playback by WidgetStateHolder.state.collectAsState()
            val current = (playback.stationId ?: lastStationId)?.let { byId[it] }
            GlanceTheme {
                WidgetContent(
                    playback = playback,
                    current = current,
                    favorites = favorites,
                    hebrew = hebrew,
                    bitmapFor = bitmapFor,
                )
            }
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 50.dp)
        private val WIDE = DpSize(250.dp, 50.dp)
        private val LARGE = DpSize(250.dp, 120.dp)
        private const val MAX_FAVORITES = 5
        private const val ARTWORK_PX = 144

        suspend fun refreshAll(context: Context) {
            ShidurWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(
    playback: WidgetPlaybackState,
    current: Station?,
    favorites: List<Station>,
    hebrew: Boolean,
    bitmapFor: (Station) -> Bitmap,
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val wide = size.width >= 200.dp
    val openPlayer = actionStartActivity<MainActivity>(actionParametersOf(OpenPlayerKey to true))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val artworkModifier = GlanceModifier.size(40.dp).cornerRadius(12.dp).clickable(openPlayer)
            if (current != null) {
                Image(
                    provider = ImageProvider(bitmapFor(current)),
                    contentDescription = current.name(hebrew),
                    modifier = artworkModifier,
                )
            } else {
                Image(
                    provider = ImageProvider(R.drawable.ic_stat_shidur),
                    contentDescription = context.getString(R.string.app_name),
                    modifier = artworkModifier,
                )
            }
            if (wide) {
                Column(
                    modifier = GlanceModifier
                        .defaultWeight()
                        .padding(horizontal = 10.dp)
                        .clickable(openPlayer),
                ) {
                    Text(
                        text = current?.name(hebrew) ?: context.getString(R.string.widget_nothing_playing),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        maxLines = 1,
                    )
                    playback.nowPlaying?.let { title ->
                        Text(
                            text = title,
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 1,
                        )
                    }
                }
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_skip_previous),
                    contentDescription = context.getString(R.string.action_previous_station),
                    onClick = actionRunCallback<WidgetPlaybackAction>(
                        actionParametersOf(WidgetPlaybackAction.ActionKey to WidgetPlaybackAction.PREVIOUS),
                    ),
                    contentColor = GlanceTheme.colors.onSurface,
                    modifier = GlanceModifier.size(40.dp),
                )
            } else {
                Spacer(GlanceModifier.defaultWeight())
            }
            CircleIconButton(
                imageProvider = ImageProvider(if (playback.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = context.getString(if (playback.isPlaying) R.string.action_pause else R.string.action_play),
                onClick = actionRunCallback<WidgetPlaybackAction>(
                    actionParametersOf(WidgetPlaybackAction.ActionKey to WidgetPlaybackAction.TOGGLE),
                ),
                backgroundColor = GlanceTheme.colors.primary,
                contentColor = GlanceTheme.colors.onPrimary,
                modifier = GlanceModifier.size(44.dp),
            )
            if (wide) {
                CircleIconButton(
                    imageProvider = ImageProvider(R.drawable.ic_skip_next),
                    contentDescription = context.getString(R.string.action_next_station),
                    onClick = actionRunCallback<WidgetPlaybackAction>(
                        actionParametersOf(WidgetPlaybackAction.ActionKey to WidgetPlaybackAction.NEXT),
                    ),
                    contentColor = GlanceTheme.colors.onSurface,
                    modifier = GlanceModifier.size(40.dp),
                )
            }
        }
        if (size.height >= 110.dp && favorites.isNotEmpty()) {
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                favorites.forEach { station ->
                    Image(
                        provider = ImageProvider(bitmapFor(station)),
                        contentDescription = station.name(hebrew),
                        modifier = GlanceModifier
                            .size(44.dp)
                            .cornerRadius(14.dp)
                            .clickable(
                                actionRunCallback<WidgetPlaybackAction>(
                                    actionParametersOf(
                                        WidgetPlaybackAction.ActionKey to WidgetPlaybackAction.PLAY_STATION,
                                        WidgetPlaybackAction.StationKey to station.id,
                                    ),
                                ),
                            ),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                }
            }
        }
    }
}

class ShidurWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShidurWidget()
}

/** Handles widget taps by sending commands to the playback service. */
class WidgetPlaybackAction : ActionCallback {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val container = (context.applicationContext as ShidurApplication).container
        val action = parameters[ActionKey] ?: return
        val favorites = container.libraryRepository.favoriteIds.first()
        val lastPlayed = container.libraryRepository.lastPlayed()
        withContext(Dispatchers.Main) {
            val connection = container.playbackConnection
            when (action) {
                TOGGLE -> {
                    if (WidgetStateHolder.state.value.stationId == null && lastPlayed != null) {
                        connection.play(lastPlayed.stationId, lastPlayed.queue)
                    } else {
                        connection.togglePlayPause()
                    }
                }
                NEXT -> connection.next()
                PREVIOUS -> connection.previous()
                PLAY_STATION -> parameters[StationKey]?.let { connection.play(it, favorites) }
            }
        }
        // Give the service time to handle the command, then let go of the connection.
        delay(RELEASE_DELAY_MS)
        withContext(Dispatchers.Main) { container.playbackConnection.releaseIfUnused() }
    }

    companion object {
        val ActionKey = ActionParameters.Key<String>("action")
        val StationKey = ActionParameters.Key<String>("station")
        const val TOGGLE = "toggle"
        const val NEXT = "next"
        const val PREVIOUS = "previous"
        const val PLAY_STATION = "play_station"
        private const val RELEASE_DELAY_MS = 5_000L
    }
}
