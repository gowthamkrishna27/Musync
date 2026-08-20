package com.musync.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Radio
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
    data object Radio : Screen("radio", "Radio", Icons.Outlined.Radio, Icons.Filled.Radio)
    data object Library : Screen("library", "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic)
    data object Search : Screen("search", "Search", Icons.Outlined.Search, Icons.Filled.Search)
    data object Settings : Screen("settings", "Settings", Icons.Outlined.Settings, Icons.Outlined.Settings)

    data object PlaylistDetail : Screen("playlist/{playlistId}", "Playlist") {
        fun createRoute(playlistId: String) = "playlist/$playlistId"
    }

    companion object {
        // 5 Floating Bottom Nav Items inspired by Apple Music (Home, New, Radio, Library, Search)
        val bottomNavItems = listOf(Home, New, Radio, Library, Search)
    }
}


