package com.musync.app.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.domain.model.Playlist
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.CardElevated
import com.musync.app.ui.theme.DeleteRed
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

import androidx.compose.foundation.layout.statusBarsPadding
import com.musync.app.ui.auth.AccountProfileCard
import com.musync.app.ui.auth.AuthBottomSheet

enum class LibraryNavTab {
    FAVORITES, PLAYLISTS, HISTORY, LOCAL
}

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.musync.app.MusyncApplication
    val authManager = app.container.authManager
    val cloudSyncManager = app.container.cloudSyncManager
    val playlistRepository = app.container.playlistRepository
    val currentUser by authManager.currentUser.collectAsState()
    val syncStatus by authManager.syncStatus.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryNavTab.FAVORITES) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAuthSheet by remember { mutableStateOf(false) }
    var trackForPlaylist by remember { mutableStateOf<com.musync.app.domain.model.Track?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    val favorites by viewModel.favorites.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val recents by viewModel.recentlyPlayed.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()

    val favoriteIds = favorites.map { it.id }.toSet()

    val tabs = listOf(
        LibraryNavTab.FAVORITES to "Favorites",
        LibraryNavTab.PLAYLISTS to "Playlists",
        LibraryNavTab.HISTORY to "History",
        LibraryNavTab.LOCAL to "Local"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        // Title: "Library"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                color = TextWhite
            )

            if (selectedTab == LibraryNavTab.PLAYLISTS) {
                IconButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New Playlist",
                        tint = TextWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Account Profile & Sync Badge
        AccountProfileCard(
            user = currentUser,
            syncStatus = syncStatus,
            onSignInClick = { showAuthSheet = true },
            onSyncClick = { cloudSyncManager.triggerSync() },
            onSignOutClick = { authManager.signOut() },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tabs: Favorites, Playlists, History, Local
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = BackgroundBlack,
            contentColor = TextWhite,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = TextWhite,
                    height = 2.dp
                )
            },
            divider = {
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(BorderStroke))
            }
        ) {
            tabs.forEach { (tab, label) ->
                val isSelected = selectedTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    text = {
                        Text(
                            label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (isSelected) TextWhite else TextGreySecondary
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        when (selectedTab) {
            LibraryNavTab.FAVORITES -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    if (favorites.isEmpty()) {
                        // Empty State Box matching reference 4
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FavoriteBorder,
                                        contentDescription = null,
                                        tint = IconGrey,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No Favorites Yet",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        ),
                                        color = TextWhite
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Add your favorite tracks by tapping\nthe heart icon.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        ),
                                        color = TextGreyMuted,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(favorites) { track ->
                            TrackItem(
                                track = track,
                                isPlaying = playbackState.currentTrack?.id == track.id,
                                isFavorite = true,
                                onClick = { viewModel.playTrack(track, favorites) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onPlayNext = { viewModel.playbackManager.playNext(track) },
                                onAddToQueue = { viewModel.playbackManager.addToQueue(track) },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                        }
                    }

                    // Section: "Recently Played" (See all)
                    if (recents.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recently Played",
                                actionText = "See all",
                                onActionClick = { selectedTab = LibraryNavTab.HISTORY }
                            )
                        }

                        val timeAgoList = listOf("2m ago", "10m ago", "1h ago", "3h ago", "5h ago", "1d ago")
                        items(recents.take(5)) { track ->
                            val index = recents.indexOf(track)
                            val timeAgo = timeAgoList.getOrElse(index) { "${index + 1}h ago" }
                            TrackItem(
                                track = track,
                                isPlaying = playbackState.currentTrack?.id == track.id,
                                isFavorite = favoriteIds.contains(track.id),
                                subtitleExtra = timeAgo,
                                onClick = { viewModel.playTrack(track, recents) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onPlayNext = { viewModel.playbackManager.playNext(track) },
                                onAddToQueue = { viewModel.playbackManager.addToQueue(track) },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                        }
                    }
                }
            }
            LibraryNavTab.PLAYLISTS -> {
                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = IconGrey,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Playlists Created",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Create playlists to group your favorite online songs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreyMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CardElevated)
                                    .border(1.dp, BorderStroke, RoundedCornerShape(8.dp))
                                    .clickable { onNavigateToPlaylist(playlist.id) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(SurfaceBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = IconGrey,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = TextWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${playlist.tracks.size} tracks",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = TextGreySecondary
                                    )
                                }
                                IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = DeleteRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
            LibraryNavTab.HISTORY -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(recents) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            onClick = { viewModel.playTrack(track, recents) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track) },
                            onPlayNext = { viewModel.playbackManager.playNext(track) },
                            onAddToQueue = { viewModel.playbackManager.addToQueue(track) },
                            onAddToPlaylist = { trackForPlaylist = track }
                        )
                    }
                }
            }
            LibraryNavTab.LOCAL -> {
                val localTracks by viewModel.localTracks.collectAsState()
                val isScanning by viewModel.isScanningLocal.collectAsState()
                val context = androidx.compose.ui.platform.LocalContext.current

                var hasPermission by remember {
                    val isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_MEDIA_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    } else {
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    mutableStateOf(isGranted)
                }

                val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasPermission = isGranted
                    if (isGranted) {
                        viewModel.loadLocalTracks()
                    }
                }

                androidx.compose.runtime.LaunchedEffect(hasPermission) {
                    if (hasPermission && localTracks.isEmpty()) {
                        viewModel.loadLocalTracks()
                    }
                }

                if (!hasPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = IconGrey,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Permission Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Grant storage permission to scan and play music from your device storage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreyMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    val perm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        android.Manifest.permission.READ_MEDIA_AUDIO
                                    } else {
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                                    }
                                    permissionLauncher.launch(perm)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF282C37),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Text("Grant Permission", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else if (isScanning) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TextWhite, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Scanning device audio...", color = TextGreySecondary, fontSize = 13.sp)
                        }
                    }
                } else if (localTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = IconGrey,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No Local Audio Found",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Music files on your device storage will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreyMuted,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadLocalTracks() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF282C37),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Text("Scan Storage", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        item {
                            SectionHeader(
                                title = "Device Tracks (${localTracks.size})",
                                actionText = "Refresh",
                                onActionClick = { viewModel.loadLocalTracks() }
                            )
                        }
                        items(localTracks) { track ->
                            TrackItem(
                                track = track,
                                isPlaying = playbackState.currentTrack?.id == track.id,
                                isFavorite = favoriteIds.contains(track.id),
                                onClick = { viewModel.playTrack(track, localTracks) },
                                onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                onPlayNext = { viewModel.playbackManager.playNext(track) },
                                onAddToQueue = { viewModel.playbackManager.addToQueue(track) },
                                onAddToPlaylist = { trackForPlaylist = track }
                            )
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name", color = TextGreySecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CardElevated,
                        unfocusedContainerColor = CardElevated,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = BorderStroke,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                                showCreateDialog = false
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showCreateDialog = false }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showAuthSheet) {
        AuthBottomSheet(
            authManager = authManager,
            onDismiss = { showAuthSheet = false }
        )
    }

    // Add to Playlist Dialog
    trackForPlaylist?.let { tr ->
        com.musync.app.ui.components.AddToPlaylistDialog(
            track = tr,
            playlistRepository = playlistRepository,
            onDismiss = { trackForPlaylist = null }
        )
    }
}
