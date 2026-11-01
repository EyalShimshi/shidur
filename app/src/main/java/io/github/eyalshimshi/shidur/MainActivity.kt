package io.github.eyalshimshi.shidur

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import io.github.eyalshimshi.shidur.ui.NavigationRequest
import io.github.eyalshimshi.shidur.ui.ShidurApp
import io.github.eyalshimshi.shidur.ui.ShidurViewModel
import io.github.eyalshimshi.shidur.ui.components.LocalStationLogos
import io.github.eyalshimshi.shidur.ui.theme.ShidurTheme
import io.github.eyalshimshi.shidur.ui.theme.isDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {

    private val viewModel: ShidurViewModel by viewModels()
    private val navigationRequest = MutableStateFlow<NavigationRequest?>(null)
    private var askedForNotifications = false

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val castAvailable = isCastAvailable()
        val stationLogos = (application as ShidurApplication).container.stationLogos

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val request by navigationRequest.collectAsStateWithLifecycle()
            val current = settings
            if (current != null) {
                ShidurTheme(current) {
                    val dark = isDarkTheme(current)
                    DisposableEffect(dark) {
                        val transparent = android.graphics.Color.TRANSPARENT
                        val style = if (dark) {
                            SystemBarStyle.dark(transparent)
                        } else {
                            SystemBarStyle.light(transparent, transparent)
                        }
                        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                        onDispose { }
                    }
                    Surface(color = MaterialTheme.colorScheme.background) {
                        CompositionLocalProvider(
                            LocalStationLogos provides if (current.stationLogos) stationLogos else null,
                        ) {
                            ShidurApp(
                                viewModel = viewModel,
                                settings = current,
                                navigationRequest = request,
                                onNavigationHandled = { navigationRequest.value = null },
                                castAvailable = castAvailable,
                                onBeforePlay = ::requestNotificationPermissionIfNeeded,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.playback.onScreenStarted()
        viewModel.warmUpLikelyStations()
        viewModel.startInstantStart()
    }

    override fun onStop() {
        // A rotation recreates the screen right away; keep the standby stream through it.
        if (!isChangingConfigurations) viewModel.stopInstantStart()
        viewModel.playback.onScreenStopped()
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (data != null && data.scheme == "shidur" && data.host == "station") {
            data.lastPathSegment?.let { stationId ->
                // Only the app's own shortcuts carry this extra; links from elsewhere just open the station.
                navigationRequest.value = if (intent.getBooleanExtra(EXTRA_PLAY_STATION, false)) {
                    NavigationRequest.PlayStation(stationId)
                } else {
                    NavigationRequest.OpenStation(stationId)
                }
            }
        } else if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) {
            navigationRequest.value = NavigationRequest.OpenPlayer
        }
    }

    /** Asks once per session, before the first playback, so the media notification can be shown. */
    private fun requestNotificationPermissionIfNeeded() {
        if (askedForNotifications || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        askedForNotifications = true
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    /** Cast needs Google Play services; devices without it simply don't show the Cast button. */
    @OptIn(UnstableApi::class)
    private fun isCastAvailable(): Boolean {
        val playServicesInstalled = try {
            packageManager.getPackageInfo(GOOGLE_PLAY_SERVICES, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (!playServicesInstalled) return false
        return runCatching { Cast.getSingletonInstance(this).castContextLoadFailure == null }.getOrDefault(false)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"

        /** Set by the app's own shortcuts: play the station in the shidur://station/ link. */
        const val EXTRA_PLAY_STATION = "play_station"
        private const val GOOGLE_PLAY_SERVICES = "com.google.android.gms"
    }
}
