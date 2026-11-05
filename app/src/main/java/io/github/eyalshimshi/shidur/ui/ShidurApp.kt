package io.github.eyalshimshi.shidur.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.eyalshimshi.shidur.R
import io.github.eyalshimshi.shidur.data.AppSettings
import io.github.eyalshimshi.shidur.model.Station
import io.github.eyalshimshi.shidur.playback.PlayerUiState
import io.github.eyalshimshi.shidur.ui.components.AppIcons
import io.github.eyalshimshi.shidur.ui.components.CrashDialog
import io.github.eyalshimshi.shidur.ui.components.LocalBottomContentPadding
import io.github.eyalshimshi.shidur.ui.components.LocalStationPress
import io.github.eyalshimshi.shidur.ui.components.MiniPlayer
import io.github.eyalshimshi.shidur.ui.components.PlayerQueue
import io.github.eyalshimshi.shidur.ui.components.PlayerSheet
import io.github.eyalshimshi.shidur.ui.components.PlayerSheetDefaults
import io.github.eyalshimshi.shidur.ui.components.PlayerSheetState
import io.github.eyalshimshi.shidur.ui.screens.AddStationScreen
import io.github.eyalshimshi.shidur.ui.screens.BrowseScreen
import io.github.eyalshimshi.shidur.ui.screens.FavoritesScreen
import io.github.eyalshimshi.shidur.ui.screens.FullPlayer
import io.github.eyalshimshi.shidur.ui.screens.HomeScreen
import io.github.eyalshimshi.shidur.ui.screens.OnboardingScreen
import io.github.eyalshimshi.shidur.ui.screens.SearchScreen
import io.github.eyalshimshi.shidur.ui.screens.SettingsScreen
import io.github.eyalshimshi.shidur.ui.theme.isDarkTheme
import io.github.eyalshimshi.shidur.ui.theme.stationColorScheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private class TopLevelDestination(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @StringRes val label: Int,
)

private val TOP_LEVEL = listOf(
    TopLevelDestination(HomeRoute, { it.hasRoute(HomeRoute::class) }, AppIcons.HomeOutlined, AppIcons.Home, R.string.nav_home),
    TopLevelDestination(BrowseRoute(), { it.hasRoute(BrowseRoute::class) }, AppIcons.RadioOutlined, AppIcons.Radio, R.string.nav_browse),
    TopLevelDestination(FavoritesRoute, { it.hasRoute(FavoritesRoute::class) }, AppIcons.FavoriteBorderOutlined, AppIcons.Favorite, R.string.nav_favorites),
)

@Composable
fun ShidurApp(
    viewModel: ShidurViewModel,
    settings: AppSettings,
    navigationRequest: NavigationRequest?,
    onNavigationHandled: () -> Unit,
    castAvailable: Boolean,
    onBeforePlay: () -> Unit,
) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val crashReport by viewModel.crashReport.collectAsStateWithLifecycle()
    val language = LocalConfiguration.current.locales[0].language
    val hebrew = language == "iw" || language == "he"

    crashReport?.let { report -> CrashDialog(report = report, onDismiss = viewModel::dismissCrashReport) }

    when {
        !library.loaded -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        !settings.onboardingDone -> OnboardingScreen(
            stations = library.stations.filter { !it.isCustom },
            hebrew = hebrew,
            onDone = viewModel::completeOnboarding,
        )
        else -> MainContent(
            viewModel = viewModel,
            library = library,
            player = player,
            settings = settings,
            hebrew = hebrew,
            navigationRequest = navigationRequest,
            onNavigationHandled = onNavigationHandled,
            castAvailable = castAvailable,
            onBeforePlay = onBeforePlay,
        )
    }
}

@Composable
private fun MainContent(
    viewModel: ShidurViewModel,
    library: LibraryUiState,
    player: PlayerUiState,
    settings: AppSettings,
    hebrew: Boolean,
    navigationRequest: NavigationRequest?,
    onNavigationHandled: () -> Unit,
    castAvailable: Boolean,
    onBeforePlay: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TOP_LEVEL.firstOrNull { top -> destination?.hierarchy?.any(top.matches) == true }
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val showRail = wide && currentTopLevel != null
    val showNavigationBar = !wide && currentTopLevel != null
    val currentStation = player.stationId?.let { library.stationsById[it] }
    val starts by viewModel.starts.collectAsStateWithLifecycle()
    val sheetState = rememberSaveable(saver = PlayerSheetState.Saver) { PlayerSheetState(initiallyExpanded = false) }
    val playerCoversContent by remember(sheetState) { derivedStateOf { sheetState.progress >= 1f } }
    val scope = rememberCoroutineScope()
    val canPinShortcuts = remember(viewModel) { viewModel.canPinShortcuts() }

    val play: (Station, List<Station>) -> Unit = { station, queue ->
        onBeforePlay()
        viewModel.play(station, queue)
    }
    val warmUp = remember(viewModel) { { station: Station -> viewModel.warmUp(station) } }

    val currentStationState = rememberUpdatedState(currentStation)
    LaunchedEffect(navigationRequest) {
        val request = navigationRequest ?: return@LaunchedEffect
        when (request) {
            is NavigationRequest.OpenStation -> viewModel.loadStation(request.stationId)
            is NavigationRequest.PlayStation -> library.stationsById[request.stationId]?.let { station ->
                // Like tapping it in Favorites: swiping in the player moves through the favorites.
                play(station, if (station.id in library.favoriteIds) library.favorites else listOf(station))
            }
            NavigationRequest.OpenPlayer -> Unit
        }
        // The player appears once the playback service reports a station.
        val station = withTimeoutOrNull(OPEN_PLAYER_TIMEOUT_MS) {
            snapshotFlow { currentStationState.value }.first { it != null }
        }
        if (station != null) sheetState.expand()
        onNavigationHandled()
    }
    // Nothing to play (e.g. playback was cleared): the next station starts with the mini player.
    val nothingLoaded = player.connected && currentStation == null
    LaunchedEffect(nothingLoaded) {
        if (nothingLoaded) sheetState.snapTo(expanded = false)
    }

    val density = LocalDensity.current
    var measuredNavigationBarHeight by remember { mutableIntStateOf(0) }
    val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navigationBarHeight = if (measuredNavigationBarHeight > 0) {
        with(density) { measuredNavigationBarHeight.toDp() }
    } else {
        NAVIGATION_BAR_HEIGHT + systemBottom
    }
    val sheetBottomInset = if (showNavigationBar) navigationBarHeight else systemBottom
    val contentBottomPadding = sheetBottomInset + (if (currentStation != null) PlayerSheetDefaults.MiniSpace else 0.dp)
    val contentSides = if (showRail) {
        WindowInsetsSides.Top + WindowInsetsSides.End
    } else {
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
    }

    CompositionLocalProvider(LocalStationPress provides warmUp) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (showRail) {
                NavigationRail {
                    TOP_LEVEL.forEach { top ->
                        val selected = top == currentTopLevel
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateTopLevel(top) },
                            icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                // Pure black stays black (no wash) so OLED pixels stay off.
                if (settings.backgroundGradient && MaterialTheme.colorScheme.background != Color.Black) TopWash()
                CompositionLocalProvider(LocalBottomContentPadding provides contentBottomPadding) {
                    ShidurNavHost(
                        navController = navController,
                        viewModel = viewModel,
                        library = library,
                        player = player,
                        settings = settings,
                        hebrew = hebrew,
                        onPlay = play,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(contentSides))
                            // The open player covers the screens; keep them out of TalkBack's reach.
                            .then(if (playerCoversContent) Modifier.clearAndSetSemantics { } else Modifier),
                    )
                }
                if (showNavigationBar) {
                    ShidurNavigationBar(
                        current = currentTopLevel,
                        gradient = settings.backgroundGradient,
                        onSelect = { top -> navController.navigateTopLevel(top) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .onSizeChanged { measuredNavigationBarHeight = it.height }
                            .graphicsLayer {
                                // Slides away as the player opens.
                                val p = sheetState.progress
                                translationY = size.height * p
                                alpha = 1f - p
                            }
                            .then(if (playerCoversContent) Modifier.clearAndSetSemantics { } else Modifier),
                    )
                }
                if (currentStation != null) {
                    val dark = isDarkTheme(settings)
                    val appScheme = MaterialTheme.colorScheme
                    val playerScheme = if (settings.stationTint) {
                        remember(currentStation.color, dark, settings.pureBlack) {
                            stationColorScheme(currentStation.color, dark, settings.pureBlack)
                        }
                    } else {
                        appScheme
                    }
                    val queue = remember(currentStation, player.queueIds, player.currentIndex, library.stationsById) {
                        PlayerQueue.of(currentStation, player, library.stationsById)
                    }
                    val lastStart = starts.firstOrNull()
                        ?.takeIf { settings.expertMode && it.stationId == currentStation.id }
                    MaterialTheme(colorScheme = playerScheme) {
                        PlayerSheet(
                            state = sheetState,
                            bottomInset = sheetBottomInset,
                            miniColor = playerScheme.secondaryContainer,
                            miniContentColor = playerScheme.onSecondaryContainer,
                            fullColor = playerScheme.surface,
                            fullContentColor = playerScheme.onSurface,
                            mini = {
                                MiniPlayer(
                                    station = currentStation,
                                    queue = queue,
                                    hebrew = hebrew,
                                    player = player,
                                    onSelectStation = viewModel::selectStationAt,
                                    onTogglePlay = viewModel::togglePlayPause,
                                )
                            },
                            full = {
                                FullPlayer(
                                    station = currentStation,
                                    queue = queue,
                                    library = library,
                                    player = player,
                                    lastStart = lastStart,
                                    backgroundGradient = settings.backgroundGradient,
                                    hebrew = hebrew,
                                    castAvailable = castAvailable,
                                    onCollapse = { scope.launch { sheetState.collapse() } },
                                    onTogglePlay = viewModel::togglePlayPause,
                                    onNext = viewModel::next,
                                    onPrevious = viewModel::previous,
                                    onSelectStation = viewModel::selectStationAt,
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onSetSleepTimer = viewModel::setSleepTimer,
                                    onExtendSleepTimer = { end -> viewModel.extendSleepTimer(end, 10) },
                                    onCancelSleepTimer = viewModel::cancelSleepTimer,
                                    onPinShortcut = if (canPinShortcuts) {
                                        { station -> viewModel.pinShortcut(station, hebrew) }
                                    } else {
                                        null
                                    },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShidurNavHost(
    navController: NavHostController,
    viewModel: ShidurViewModel,
    library: LibraryUiState,
    player: PlayerUiState,
    settings: AppSettings,
    hebrew: Boolean,
    onPlay: (Station, List<Station>) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> {
            HomeScreen(
                library = library,
                player = player,
                hebrew = hebrew,
                onPlay = onPlay,
                onOpenSearch = { navController.navigate(SearchRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenCollection = { collectionId -> navController.navigate(BrowseRoute(collectionId)) },
                onOpenFavorites = { navController.navigateTopLevel(TOP_LEVEL[2]) },
            )
        }
        composable<BrowseRoute> { entry ->
            val route = entry.toRoute<BrowseRoute>()
            BrowseScreen(
                library = library,
                player = player,
                hebrew = hebrew,
                initialCollection = route.collectionId,
                onPlay = onPlay,
                onToggleFavorite = viewModel::toggleFavorite,
                onOpenSearch = { navController.navigate(SearchRoute) },
            )
        }
        composable<FavoritesRoute> {
            FavoritesScreen(
                library = library,
                player = player,
                hebrew = hebrew,
                onPlay = onPlay,
                onReorder = viewModel::reorderFavorites,
                onRemove = { station ->
                    if (station.isCustom) viewModel.deleteCustomStation(station.id) else viewModel.toggleFavorite(station.id)
                },
                onAddByUrl = { navController.navigate(AddStationRoute()) },
                onBrowse = { navController.navigateTopLevel(TOP_LEVEL[1]) },
            )
        }
        composable<SearchRoute> {
            SearchScreen(
                library = library,
                player = player,
                hebrew = hebrew,
                onBack = { navController.popBackStack() },
                onPlay = onPlay,
                onToggleFavorite = viewModel::toggleFavorite,
                onAddUrl = { url -> navController.navigate(AddStationRoute(url)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                settings = settings,
                catalogVersion = library.catalogVersion,
                stationsById = library.stationsById,
                hebrew = hebrew,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddStationRoute> { entry ->
            val route = entry.toRoute<AddStationRoute>()
            AddStationScreen(
                initialUrl = route.url,
                onBack = { navController.popBackStack() },
                onProbe = viewModel::probeStream,
                onSave = { name, url ->
                    viewModel.addCustomStation(name, url) { navController.popBackStack() }
                },
            )
        }
    }
}

/** The bottom navigation bar; with gradients on it fades into the content above it. */
@Composable
private fun ShidurNavigationBar(
    current: TopLevelDestination?,
    gradient: Boolean,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surfaceContainer
    val background = if (gradient) {
        Modifier.background(
            Brush.verticalGradient(
                0f to container.copy(alpha = 0f),
                0.3f to container.copy(alpha = 0.94f),
                1f to container,
            ),
        )
    } else {
        Modifier.background(container)
    }
    NavigationBar(
        modifier = modifier.then(background),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        TOP_LEVEL.forEach { top ->
            val selected = top == current
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(top) },
                icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                label = { Text(stringResource(top.label)) },
            )
        }
    }
}

/** A soft wash of the accent color behind the top of every screen. */
@Composable
private fun TopWash() {
    val color = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_WASH_HEIGHT)
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), Color.Transparent))),
    )
}

private val NAVIGATION_BAR_HEIGHT = 80.dp
private val TOP_WASH_HEIGHT = 320.dp

/** How long a notification or link tap waits for the playback service before giving up. */
private const val OPEN_PLAYER_TIMEOUT_MS = 3_000L

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
