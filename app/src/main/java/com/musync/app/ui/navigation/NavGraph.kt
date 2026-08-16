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
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextWhite

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
                    navDeepLink { uriPattern = "musync://home" },
                    navDeepLink { uriPattern = "musync://home" }
                )
            ) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }

            composable(
                route = Screen.Search.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://search" },
                    navDeepLink { uriPattern = "musync://search" }
                )
            ) {
                SearchScreen(viewModel = searchViewModel)
            }

            composable(
                route = Screen.Library.route,
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://library" },
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
                    navDeepLink { uriPattern = "musync://settings" },
                    navDeepLink { uriPattern = "musync://settings" }
                )
            ) {
                SettingsScreen(viewModel = settingsViewModel)
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType }),
                deepLinks = listOf(
                    navDeepLink { uriPattern = "musync://playlist/{playlistId}" },
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
                    navDeepLink { uriPattern = "musync://track/{id}" },
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
                    onNavigateToSearch = { navController.navigate(Screen.Search.route) }
                )
            }
        }

        // True Floating Glassmorphism Bottom Bar Overlay with Dark Frosted Glass Background
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val isPlayingTrack = playbackState.currentTrack != null

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Transparent)
                .padding(start = 14.dp, end = 14.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = if (isPlayingTrack) Modifier.fillMaxWidth() else Modifier.wrapContentWidth(),
                horizontalArrangement = if (isPlayingTrack) Arrangement.spacedBy(8.dp) else Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. LEFT SIDE: Mini Music Player (Dark Frosted Glass Pill)
                AnimatedVisibility(
                    visible = isPlayingTrack,
                    enter = fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) +
                            slideInHorizontally(initialOffsetX = { -it / 2 }, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                            expandHorizontally(),
                    exit = fadeOut() + slideOutHorizontally() + shrinkHorizontally(),
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    if (playbackState.currentTrack != null) {
                        val track = playbackState.currentTrack!!
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(36.dp))
                                .background(Color(0xF2181A24))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(36.dp))
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    showNowPlaying = true
                                }
                                .padding(start = 8.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Circular Vinyl Artwork
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF111111))
                                        .border(1.5.dp, Color(0x55444444), CircleShape)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!track.artworkUrl.isNullOrBlank()) {
                                        coil.compose.AsyncImage(
                                            model = track.artworkUrl,
                                            contentDescription = track.title,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        com.musync.app.ui.components.DefaultArtworkView(
                                            modifier = Modifier.fillMaxSize(),
                                            iconSize = 20.dp,
                                            shape = CircleShape
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Track Title & Artist
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = track.title,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (playbackState.isVideoMode) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x6664B5F6))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Text(
                                                    text = "VIDEO",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = track.artist.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = Color(0xFFB3B3B3),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(6.dp))

                                // Dark Glass Play/Pause Button
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2C303E))
                                        .border(1.dp, Color(0x44FFFFFF), CircleShape)
                                        .clickable {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            playerViewModel.togglePlay()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) androidx.compose.material.icons.Icons.Default.Pause else androidx.compose.material.icons.Icons.Default.PlayArrow,
                                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. RIGHT SIDE: Floating Navigation Pill (Home, Library, Settings with Dark Glass)
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color(0xF2181A24))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(36.dp))
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Row(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Home
                        val isHome = currentRoute == Screen.Home.route
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isHome) Color(0x35FFFFFF) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Filled.Home,
                                contentDescription = "Home",
                                tint = if (isHome) Color.White else Color(0xFF9E9E9E),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 2. Library
                        val isLibrary = currentRoute == Screen.Library.route
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isLibrary) Color(0x35FFFFFF) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    navController.navigate(Screen.Library.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.LibraryMusic,
                                contentDescription = "Library",
                                tint = if (isLibrary) Color.White else Color(0xFF9E9E9E),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // 3. Settings
                        val isSettings = currentRoute == Screen.Settings.route
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isSettings) Color(0x35FFFFFF) else Color.Transparent)
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    navController.navigate(Screen.Settings.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (isSettings) Color.White else Color(0xFF9E9E9E),
                                modifier = Modifier.size(22.dp)
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

