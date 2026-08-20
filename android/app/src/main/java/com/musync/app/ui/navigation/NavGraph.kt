package com.musync.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.musync.app.ui.radio.RadioScreen
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
            container.localAudioScanner,
            container.playbackManager
        )
    )

    val searchViewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.Factory(
            container.musicRepository,
            container.favoritesRepository,
            container.playbackManager
        )
    )

    val libraryViewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(
            container.favoritesRepository,
            container.playlistRepository,
            container.recentlyPlayedRepository,
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
            container.authManager,
            container.cloudSyncManager
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
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
                route = Screen.Radio.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://radio" }
                )
            ) {
                RadioScreen()
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
                SettingsScreen(viewModel = settingsViewModel)
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
                        container.playbackManager
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 22.dp, top = 6.dp),
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

            // 2. 5-TAB FLOATING NAVIGATION BAR (Apple Music style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xF21B1B1E))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        val activeColor = AppleMusicRed
                        val inactiveColor = Color(0xFF8E8E93)

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val icon = if (isSelected) screen.selectedIcon ?: screen.unselectedIcon else screen.unselectedIcon
                            if (icon != null) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) activeColor else inactiveColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) activeColor else inactiveColor
                            )
                        }
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

