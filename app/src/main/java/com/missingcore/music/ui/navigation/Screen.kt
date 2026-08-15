package com.missingcore.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home)
    data object Search : Screen("search", "Search", Icons.Outlined.Search)
    data object Library : Screen("library", "Library", Icons.Outlined.LibraryMusic)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }

    companion object {
        // Search removed from bottom nav bar as requested: 3 floating items (Home, Library, Settings)
        val bottomNavItems = listOf(Home, Library, Settings)
    }
}
