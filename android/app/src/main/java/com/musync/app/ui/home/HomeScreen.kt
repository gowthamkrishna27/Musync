package com.musync.app.ui.home

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.musync.app.domain.model.Track
import com.musync.app.ui.components.AddToPlaylistDialog
import com.musync.app.ui.components.ErrorView
import com.musync.app.ui.components.LoadingView
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.musync.app.MusyncApplication
    val playlistRepository = app.container.playlistRepository

    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()

    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }

    val favoriteIds = favorites.map { it.id }.toSet()
    val moodPills = listOf("Podcasts", "Feel good", "Romance", "Relax", "Party", "Workout", "Focus", "Telugu", "Tamil", "Hindi", "All")
    var selectedMood by remember { mutableStateOf("Feel good") }

    val editorialItems = remember(
        uiState.trendingTracks,
        uiState.teluguTracks,
        uiState.tamilTracks,
        uiState.hindiTracks,
        uiState.globalTracks
    ) {
        buildEditorialCarouselItems(
            trendingTracks = uiState.trendingTracks,
            teluguTracks = uiState.teluguTracks,
            tamilTracks = uiState.tamilTracks,
            hindiTracks = uiState.hindiTracks,
            globalTracks = uiState.globalTracks
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
    ) {
        when {
            uiState.isLoading && uiState.trendingTracks.isEmpty() && uiState.localTracks.isEmpty() -> {
                LoadingView(message = "Loading music catalog...")
            }
            uiState.errorMessage != null && uiState.trendingTracks.isEmpty() && uiState.localTracks.isEmpty() -> {
                ErrorView(
                    message = uiState.errorMessage ?: "An error occurred",
                    onRetry = { viewModel.loadHomeData() }
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    // 1. Top App Header: Large "Home" Title + Profile Avatar Button
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Musync",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp
                                    ),
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                com.musync.app.ui.navigation.NetworkQualityDot(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .padding(bottom = 6.dp)
                                )
                            }

                            // Settings Button (Apple Music style)
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF242429))
                                    .border(1.5.dp, Color(0x44FFFFFF), CircleShape)
                                    .clickable {
                                        if (onNavigateToSettings != null) {
                                            onNavigateToSettings()
                                        } else {
                                            onNavigateToSearch()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 2. Mood & Category Filter Pills (Apple Music / YouTube Music style)
                    if (!uiState.isOffline && uiState.searchQuery.isBlank()) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(moodPills, key = { it }) { mood ->
                                    val isSelected = selectedMood == mood
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0xFF2E2E36) else Color(0xFF1B1B20))
                                            .border(1.dp, if (isSelected) Color(0x66FFFFFF) else Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                                            .clickable {
                                                selectedMood = mood
                                                if (mood in listOf("Telugu", "Tamil", "Hindi", "All")) {
                                                    viewModel.selectLanguage(mood)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = mood,
                                            color = if (isSelected) Color.White else Color(0xFFB0B0B8),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // 3. Top Playlists / Hero Showcase Carousel with 3D Depth Transition & Infinite Looping
                    if (!uiState.isOffline && uiState.searchQuery.isBlank() && editorialItems.isNotEmpty()) {
                        item {
                            PremiumPlaylistCarousel(
                                items = editorialItems,
                                isPlaying = playbackState.isPlaying,
                                currentTrackId = playbackState.currentTrack?.id,
                                onPlayTrack = { track, tracks ->
                                    viewModel.playTrack(track, tracks)
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }


                    // 5. "Recently Played" Section
                    if (!uiState.isOffline && uiState.searchQuery.isBlank() && uiState.trendingTracks.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Recently Played",
                                actionText = "See all >",
                                onActionClick = { onNavigateToSearch() }
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                itemsIndexed(uiState.trendingTracks.take(8), key = { index, track -> "recent_${track.id}_$index" }) { _, track ->
                                    GlassSongCard(
                                        track = track,
                                        isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                        onClick = { viewModel.playTrack(track, uiState.trendingTracks) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // 3. Offline Status Banner
                    if (uiState.isOffline) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x33E65100))
                                    .border(1.dp, Color(0x55FF9800), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Offline",
                                            tint = Color(0xFFFFB74D),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "No Internet Connection • Offline Mode",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                    Text(
                                        text = "Retry",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        ),
                                        color = Color(0xFFFFB74D),
                                        modifier = Modifier.clickable { viewModel.loadHomeData() }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Search Results Mode
                    if (uiState.searchQuery.isNotBlank()) {
                        if (uiState.isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = TextWhite,
                                        strokeWidth = 2.5.dp,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        } else if (uiState.searchResults.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No results found for \"${uiState.searchQuery}\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextGreyMuted
                                    )
                                }
                            }
                        } else {
                            item {
                                SectionHeader(title = "Search Results (${uiState.searchResults.size})")
                            }
                            items(uiState.searchResults, key = { it.id }) { track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = playbackState.currentTrack?.id == track.id,
                                    isFavorite = favoriteIds.contains(track.id),
                                    onClick = { viewModel.playTrack(track, uiState.searchResults) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                    onPlayNext = { viewModel.playNext(track) },
                                    onAddToQueue = { viewModel.addToQueue(track) },
                                    onAddToPlaylist = { trackForPlaylist = track }
                                )
                            }
                        }
                    } else if (uiState.isOffline) {
                        // Offline Mode: Local Device Tracks
                        if (uiState.localTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Local Device Tracks (${uiState.localTracks.size})",
                                    actionText = "Refresh",
                                    onActionClick = { viewModel.loadHomeData() }
                                )
                            }
                            items(uiState.localTracks, key = { it.id }) { track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = playbackState.currentTrack?.id == track.id,
                                    isFavorite = favoriteIds.contains(track.id),
                                    onClick = { viewModel.playTrack(track, uiState.localTracks) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                    onPlayNext = { viewModel.playNext(track) },
                                    onAddToQueue = { viewModel.addToQueue(track) },
                                    onAddToPlaylist = { trackForPlaylist = track }
                                )
                            }
                        }
                    } else {
                        // 5. Real Songs Ordered in Language Categories (Telugu -> Tamil -> Hindi -> Other)

                        // SECTION 1: TELUGU SONGS (Glass UI Horizontal Carousel)
                        if ((uiState.selectedLanguage == "All" || uiState.selectedLanguage == "Telugu") && uiState.teluguTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Top Telugu Songs",
                                    actionText = "Play all",
                                    onActionClick = {
                                        uiState.teluguTracks.firstOrNull()?.let {
                                            viewModel.playTrack(it, uiState.teluguTracks)
                                        }
                                    }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(uiState.teluguTracks, key = { index, track -> "te_${track.id}_$index" }) { _, track ->
                                        GlassSongCard(
                                            track = track,
                                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                            onClick = { viewModel.playTrack(track, uiState.teluguTracks) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // SECTION 2: TAMIL SONGS (Glass UI Horizontal Carousel)
                        if ((uiState.selectedLanguage == "All" || uiState.selectedLanguage == "Tamil") && uiState.tamilTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Top Tamil Songs",
                                    actionText = "Play all",
                                    onActionClick = {
                                        uiState.tamilTracks.firstOrNull()?.let {
                                            viewModel.playTrack(it, uiState.tamilTracks)
                                        }
                                    }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(uiState.tamilTracks, key = { index, track -> "ta_${track.id}_$index" }) { _, track ->
                                        GlassSongCard(
                                            track = track,
                                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                            onClick = { viewModel.playTrack(track, uiState.tamilTracks) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // SECTION 3: HINDI SONGS (Glass UI Horizontal Carousel)
                        if ((uiState.selectedLanguage == "All" || uiState.selectedLanguage == "Hindi") && uiState.hindiTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Top Hindi Songs",
                                    actionText = "Play all",
                                    onActionClick = {
                                        uiState.hindiTracks.firstOrNull()?.let {
                                            viewModel.playTrack(it, uiState.hindiTracks)
                                        }
                                    }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(uiState.hindiTracks, key = { index, track -> "hi_${track.id}_$index" }) { _, track ->
                                        GlassSongCard(
                                            track = track,
                                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                            onClick = { viewModel.playTrack(track, uiState.hindiTracks) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // SECTION 4: GLOBAL & OTHER LANGUAGES (Glass UI Horizontal Carousel)
                        if ((uiState.selectedLanguage == "All" || listOf("English", "Malayalam", "Kannada", "Punjabi").contains(uiState.selectedLanguage)) && uiState.globalTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = if (uiState.selectedLanguage == "All") "Global & Regional Hits" else "${uiState.selectedLanguage} Hits",
                                    actionText = "Play all",
                                    onActionClick = {
                                        uiState.globalTracks.firstOrNull()?.let {
                                            viewModel.playTrack(it, uiState.globalTracks)
                                        }
                                    }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(uiState.globalTracks, key = { index, track -> "gl_${track.id}_$index" }) { _, track ->
                                        GlassSongCard(
                                            track = track,
                                            isPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying,
                                            onClick = { viewModel.playTrack(track, uiState.globalTracks) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // SECTION 5: RECOMMENDED / ALL-TIME POPULAR TRACKS (Vertical List)
                        if (uiState.trendingTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Recommended For You",
                                    actionText = "See all",
                                    onActionClick = { }
                                )
                            }

                            itemsIndexed(uiState.trendingTracks, key = { index, track -> "tr_${track.id}_$index" }) { _, track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = playbackState.currentTrack?.id == track.id,
                                    isFavorite = favoriteIds.contains(track.id),
                                    onClick = { viewModel.playTrack(track, uiState.trendingTracks) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                    onPlayNext = { viewModel.playNext(track) },
                                    onAddToQueue = { viewModel.addToQueue(track) },
                                    onAddToPlaylist = { trackForPlaylist = track }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add to Playlist Dialog
        val currentTrackForPlaylist = trackForPlaylist
        if (currentTrackForPlaylist != null) {
            AddToPlaylistDialog(
                track = currentTrackForPlaylist,
                playlistRepository = playlistRepository,
                onDismiss = { trackForPlaylist = null }
            )
        }
    }
}


/**
 * Pure Glass UI Song Card with Smooth Cached Artwork, Translucent Background & Frosted Border
 */
@Composable
private fun GlassSongCard(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageRequest = androidx.compose.runtime.remember(track.artworkUrl, track.id) {
        com.musync.app.core.image.ImageQualityHelper.buildOptimizedImageRequest(context, track.artworkUrl, track.id)
    }

    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        // Song Artwork Container with Apple Music Tile shape
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF18181B))
                .border(1.dp, if (isPlaying) com.musync.app.ui.theme.AppleMusicRed else Color(0x18FFFFFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank() || track.id.isNotBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = IconGrey,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Apple Music style badge in top left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0x77000000))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "M",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(com.musync.app.ui.theme.AppleMusicRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Playing",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Song Title
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            color = if (isPlaying) com.musync.app.ui.theme.AppleMusicRed else TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(1.dp))

        // Artist Name
        Text(
            text = track.artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = TextGreySecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}


