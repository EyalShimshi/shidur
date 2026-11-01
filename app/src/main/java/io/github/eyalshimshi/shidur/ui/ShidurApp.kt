package io.github.eyalshimshi.shidur.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
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
import io.github.eyalshimshi.shidur.ui.components.MiniPlayer
import io.github.eyalshimshi.shidur.ui.screens.AddStationScreen
import io.github.eyalshimshi.shidur.ui.screens.BrowseScreen
import io.github.eyalshimshi.shidur.ui.screens.FavoritesScreen
import io.github.eyalshimshi.shidur.ui.screens.HomeScreen
import io.github.eyalshimshi.shidur.ui.screens.OnboardingScreen
import io.github.eyalshimshi.shidur.ui.screens.PlayerScreen
import io.github.eyalshimshi.shidur.ui.screens.SearchScreen
import io.github.eyalshimshi.shidur.ui.screens.SettingsScreen

private class TopLevelDestination(
    val route: Any,
    val matches: (NavDestination) -> Boolean,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    @StringRes val label: Int,
)

private val TOP_LEVEL = listOf(
    TopLevelDestination(HomeRoute, { it.hasRoute(HomeRoute::class) }, Icons.Outlined.Home, Icons.Rounded.Home, R.string.nav_home),
    TopLevelDestination(BrowseRoute(), { it.hasRoute(BrowseRoute::class) }, Icons.Outlined.Radio, Icons.Rounded.Radio, R.string.nav_browse),
    TopLevelDestination(FavoritesRoute, { it.hasRoute(FavoritesRoute::class) }, Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite, R.string.nav_favorites),
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
    val language = LocalConfiguration.current.locales[0].language
    val hebrew = language == "iw" || language == "he"

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

@OptIn(ExperimentalLayoutApi::class)
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
    val onPlayerScreen = destination?.hasRoute(PlayerRoute::class) == true
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val currentStation = player.stationId?.let { library.stationsById[it] }

    val play: (Station, List<Station>) -> Unit = { station, queue ->
        onBeforePlay()
        viewModel.play(station, queue)
    }

    LaunchedEffect(navigationRequest) {
        when (navigationRequest) {
            NavigationRequest.OpenPlayer -> navController.navigate(PlayerRoute) { launchSingleTop = true }
            is NavigationRequest.OpenStation -> {
                viewModel.loadStation(navigationRequest.stationId)
                navController.navigate(PlayerRoute) { launchSingleTop = true }
            }
            null -> Unit
        }
        if (navigationRequest != null) onNavigationHandled()
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (wide && currentTopLevel != null) {
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
        Scaffold(
            modifier = Modifier.weight(1f),
            bottomBar = {
                val showNavigationBar = !wide && currentTopLevel != null
                val showMiniPlayer = currentStation != null && !onPlayerScreen
                Column {
                    if (currentStation != null && !onPlayerScreen) {
                        MiniPlayer(
                            station = currentStation,
                            hebrew = hebrew,
                            player = player,
                            onOpen = { navController.navigate(PlayerRoute) { launchSingleTop = true } },
                            onTogglePlay = viewModel::togglePlayPause,
                            modifier = if (showNavigationBar) Modifier else Modifier.navigationBarsPadding(),
                        )
                    }
                    if (showNavigationBar) {
                        NavigationBar {
                            TOP_LEVEL.forEach { top ->
                                val selected = top == currentTopLevel
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navController.navigateTopLevel(top) },
                                    icon = { Icon(if (selected) top.selectedIcon else top.icon, contentDescription = null) },
                                    label = { Text(stringResource(top.label)) },
                                )
                            }
                        }
                    }
                    // Keeps screens without a bottom bar clear of the system navigation bar.
                    if (!showMiniPlayer && !showNavigationBar) {
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        library = library,
                        player = player,
                        hebrew = hebrew,
                        onPlay = play,
                        onToggleFavorite = viewModel::toggleFavorite,
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
                        onPlay = play,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onOpenSearch = { navController.navigate(SearchRoute) },
                    )
                }
                composable<FavoritesRoute> {
                    FavoritesScreen(
                        library = library,
                        player = player,
                        hebrew = hebrew,
                        onPlay = play,
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
                        onPlay = play,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onAddUrl = { url -> navController.navigate(AddStationRoute(url)) },
                    )
                }
                composable<PlayerRoute> {
                    PlayerScreen(
                        station = currentStation,
                        library = library,
                        player = player,
                        settings = settings,
                        hebrew = hebrew,
                        castAvailable = castAvailable,
                        onClose = { navController.popBackStack() },
                        onTogglePlay = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onSetSleepTimer = viewModel::setSleepTimer,
                        onExtendSleepTimer = { end -> viewModel.extendSleepTimer(end, 10) },
                        onCancelSleepTimer = viewModel::cancelSleepTimer,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        settings = settings,
                        catalogVersion = library.catalogVersion,
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
    }
}

private fun NavHostController.navigateTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
