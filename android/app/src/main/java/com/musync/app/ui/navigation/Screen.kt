package com.musync.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val unselectedIcon: ImageVector? = null,
    val selectedIcon: ImageVector? = null
) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object New : Screen("new", "New", Icons.Outlined.GridView, Icons.Filled.GridView)
    data object Offline : Screen("offline", "Offline", Icons.Outlined.DownloadForOffline, Icons.Filled.DownloadForOffline)
    data object Library : Screen("library", "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic)
    data object Search : Screen("search", "Search", Icons.Outlined.Search, Icons.Filled.Search)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Outlined.Settings)

    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }

    companion object {
        // 4 Primary tabs inside the left floating capsule dock
        val primaryDockItems: List<Screen>
            get() = listOf(Home, New, Offline, Library)

        // All 5 bottom nav items including the detached search circle button
        val bottomNavItems: List<Screen>
            get() = listOf(Home, New, Offline, Library, Search)
    }
}


