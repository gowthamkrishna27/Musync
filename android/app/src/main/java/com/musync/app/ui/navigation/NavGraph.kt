package com.musync.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.ui.theme.AppleMusicPink
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.musync.app.MusyncApplication
import com.musync.app.ui.components.MiniPlayer
import com.musync.app.ui.home.HomeScreen
import com.musync.app.ui.home.HomeViewModel
import com.musync.app.ui.library.LibraryScreen
import com.musync.app.ui.library.LibraryViewModel
import com.musync.app.ui.player.NowPlayingSheet
import com.musync.app.ui.player.PlayerViewModel
import com.musync.app.ui.player.QueueSheet
import com.musync.app.ui.playlist.PlaylistDetailScreen
import com.musync.app.ui.playlist.PlaylistViewModel
import com.musync.app.ui.search.SearchScreen
import com.musync.app.ui.search.SearchViewModel
import com.musync.app.ui.settings.SettingsScreen
import com.musync.app.ui.settings.SettingsViewModel
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import com.musync.app.ui.browse.NewScreen
import com.musync.app.ui.offline.OfflineScreen
import com.musync.app.ui.theme.AppleMusicRed
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextWhite


/**
 * Live-reactive colored dot indicating current network quality.
 * 🟢 Green = WiFi / 5G / LTE  |  🟡 Amber = 3G  |  🔴 Red = 2G / offline
 * Updates automatically whenever network type changes via ConnectivityManager.NetworkCallback.
 */
@Composable
fun NetworkQualityDot(isBuffering: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // produceState + NetworkCallback: re-emits quality string whenever the
    // active network changes (WiFi <-> LTE <-> 3G <-> offline).
    val quality by androidx.compose.runtime.produceState(
        initialValue = com.musync.app.core.network.NetworkQualityHelper.getRecommendedQuality(context)
    ) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (cm != null) {
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    value = com.musync.app.core.network.NetworkQualityHelper.getRecommendedQuality(context)
                }
                override fun onLost(network: android.net.Network) {
                    value = "saver" // treat as very slow / offline
                }
                override fun onCapabilitiesChanged(
                    network: android.net.Network,
                    caps: android.net.NetworkCapabilities
                ) {
                    value = com.musync.app.core.network.NetworkQualityHelper.getRecommendedQuality(context)
                }
            }

            try {
                val request = android.net.NetworkRequest.Builder().build()
                cm.registerNetworkCallback(request, callback)
            } catch (e: Exception) {
                // Ignore if permission or restricted mode
            }

            // Unregister when the composable leaves composition
            awaitDispose {
                try {
                    cm.unregisterNetworkCallback(callback)
                } catch (e: Exception) {
                    // Ignore if already unregistered
                }
            }
        }
    }

    val dotColor = when (quality) {
        "saver" -> Color(0xFFFF4444)   // red — 2G / offline
        "low"   -> Color(0xFFFFB300)   // amber — 3G
        else    -> Color(0xFF4CAF50)   // green — LTE / 5G / WiFi
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(dotColor)
    )
}

@Composable
fun MainApp(
    app: MusyncApplication,
    navController: NavHostController
) {
    val container = app.container

    // ViewModels
    val homeViewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.Factory(
            container.musicRepository,
            container.favoritesRepository,
            container.playlistRepository,
            container.recentlyPlayedRepository,
            container.localAudioScanner,
            container.playbackManager,
            container.preferencesManager
        )
    )

    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            container.musicRepository,
            container.favoritesRepository,
            container.downloadRepository,
            container.localAudioScanner,
            container.playbackManager
        )
    )

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(
            container.favoritesRepository,
            container.playlistRepository,
            container.recentlyPlayedRepository,
            container.downloadRepository,
            container.localAudioScanner,
            container.playbackManager
        )
    )

    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container.preferencesManager,
            container.musicRepository,
            container.universalMusicProvider,
            container.beatHapticManager,
            container.downloadRepository,
            container.authManager,
            container.cloudSyncManager,
            container.audioEffectManager
        )
    )

    val playerViewModel: PlayerViewModel = viewModel(
        factory = PlayerViewModel.Factory(
            container.playbackManager,
            container.favoritesRepository
        )
    )

    val playbackState by container.playbackManager.playbackState.collectAsState()
    val favorites by container.favoritesRepository.getFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val initialLandingPage = remember {
        when (container.preferencesManager.getDefaultLandingPage()) {
            "Discover" -> Screen.New.route
            "Library" -> Screen.Library.route
            "Offline" -> Screen.Offline.route
            else -> Screen.Home.route
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        NavHost(
            navController = navController,
            startDestination = initialLandingPage,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(170, easing = FastOutSlowInEasing)) +
                slideInHorizontally(
                    initialOffsetX = { (it * 0.08f).toInt() },
                    animationSpec = tween(170, easing = FastOutSlowInEasing)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(170, easing = LinearOutSlowInEasing))
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(130, easing = FastOutLinearInEasing)) +
                slideOutHorizontally(
                    targetOffsetX = { (it * 0.08f).toInt() },
                    animationSpec = tween(130, easing = FastOutLinearInEasing)
                )
            }
        ) {
            composable(
                route = Screen.Home.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://home" }
                )
            ) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    },
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(
                route = Screen.New.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://new" }
                )
            ) {
                NewScreen(viewModel = homeViewModel)
            }

            composable(
                route = Screen.Offline.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://offline" }
                )
            ) {
                OfflineScreen()
            }

            composable(
                route = Screen.Search.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://search" }
                )
            ) {
                SearchScreen(viewModel = searchViewModel)
            }

            composable(
                route = Screen.Library.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://library" }
                )
            ) {
                LibraryScreen(
                    viewModel = libraryViewModel,
                    onNavigateToPlaylist = { playlistId ->
                        navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
                    }
                )
            }

            composable(
                route = Screen.Settings.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://settings" }
                )
            ) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://playlist/{playlistId}" }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                val playlistViewModel: PlaylistViewModel = viewModel(
                    key = "playlist_$playlistId",
                    factory = PlaylistViewModel.Factory(
                        playlistId,
                        container.playlistRepository,
                        container.favoritesRepository,
                        container.playbackManager,
                        container.musicRepository,
                        container.realTimeRecommendationEngine
                    )
                )
                PlaylistDetailScreen(
                    viewModel = playlistViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // Deep links for direct track playback
            composable(
                route = "track/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://track/{id}" }
                )
            ) { backStackEntry ->
                val trackId = backStackEntry.arguments?.getString("id")
                if (!trackId.isNullOrBlank()) {
                    androidx.compose.runtime.LaunchedEffect(trackId) {
                        val track = container.musicRepository.getTrack(trackId).getOrNull()
                        if (track != null) {
                            container.playbackManager.play(track)
                        }
                    }
                }
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
        }

        // Apple Music Inspired Floating Dock: Mini Player + 5-Tab Navigation Bar
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val isPlayingTrack = playbackState.currentTrack != null
        val showBottomBarAndDock = currentRoute in Screen.bottomNavItems.map { it.route }

        AnimatedVisibility(
            visible = showBottomBarAndDock,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(spring(stiffness = Spring.StiffnessMediumLow)) +
                    slideOutVertically(targetOffsetY = { it })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 8.dp, top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. DOCKED FLOATING MINI PLAYER (Floats directly above navigation bar)
                AnimatedVisibility(
                    visible = isPlayingTrack,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInVertically(initialOffsetY = { it / 2 }) +
                            expandHorizontally(),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }) + shrinkHorizontally()
                ) {
                    if (playbackState.currentTrack != null) {
                        MiniPlayer(
                            playbackState = playbackState,
                            onTogglePlay = { playerViewModel.togglePlay() },
                            onSkipNext = { playerViewModel.skipNext() },
                            onSkipPrevious = { playerViewModel.skipPrevious() },
                            onClick = {
                                showNowPlaying = true
                            },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                // 2. APPLE MUSIC FLOATING DOCK: 4-TAB CAPSULE + DETACHED SEARCH CIRCLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Main 4-Tab Navigation Capsule
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(60.dp)
                            .shadow(12.dp, CircleShape, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(Color(0xF518181B))
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x38FFFFFF),
                                        Color(0x10FFFFFF)
                                    )
                                ),
                                CircleShape
                            )
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Screen.primaryDockItems.forEach { screen ->
                                val isSelected = currentRoute == screen.route
                                val activeColor = AppleMusicPink
                                val inactiveColor = Color(0xFFE5E5EA)

                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        val icon = if (isSelected) screen.selectedIcon ?: screen.unselectedIcon else screen.unselectedIcon
                                        if (icon != null) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = screen.title,
                                                tint = if (isSelected) activeColor else inactiveColor.copy(alpha = 0.55f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = screen.title,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                            ),
                                            color = if (isSelected) activeColor else inactiveColor.copy(alpha = 0.55f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Detached Floating Search Circle Button
                    val isSearchSelected = currentRoute == Screen.Search.route
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(12.dp, CircleShape, spotColor = Color.Black)
                            .clip(CircleShape)
                            .background(if (isSearchSelected) Color(0xF52A2A2E) else Color(0xF518181B))
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        if (isSearchSelected) Color(0x55FFFFFF) else Color(0x38FFFFFF),
                                        Color(0x10FFFFFF)
                                    )
                                ),
                                CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                navController.navigate(Screen.Search.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = if (isSearchSelected) AppleMusicPink else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Now Playing Sheet
        if (showNowPlaying && playbackState.currentTrack != null) {
            NowPlayingSheet(
                playbackState = playbackState,
                isFavorite = favoriteIds.contains(playbackState.currentTrack?.id),
                onDismiss = { showNowPlaying = false },
                onTogglePlay = { playerViewModel.togglePlay() },
                onSkipNext = { playerViewModel.skipNext() },
                onSkipPrevious = { playerViewModel.skipPrevious() },
                onSeekTo = { pos -> playerViewModel.seekTo(pos) },
                onToggleFavorite = { playerViewModel.toggleFavorite(playbackState.currentTrack) },
                onToggleShuffle = { playerViewModel.toggleShuffle() },
                onToggleRepeat = { playerViewModel.toggleRepeat() },
                onOpenQueue = {
                    showNowPlaying = false
                    showQueue = true
                }
            )
        }

        // Queue Sheet
        if (showQueue) {
            QueueSheet(
                playbackState = playbackState,
                onDismiss = { showQueue = false },
                onPlayTrackAtIndex = { idx -> playerViewModel.playTrackAtIndex(idx) },
                onRemoveFromQueue = { id -> playerViewModel.removeFromQueue(id) },
                onClearQueue = { playerViewModel.clearQueue() }
            )
        }
    }
}

