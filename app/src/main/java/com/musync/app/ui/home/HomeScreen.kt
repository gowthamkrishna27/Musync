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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.musync.app.ui.components.ErrorView
import com.musync.app.ui.components.LoadingView
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.StatusGreen
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSearch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()

    val favoriteIds = favorites.map { it.id }.toSet()
    val languagePills = listOf("All", "Telugu", "Tamil", "Hindi", "English", "Malayalam", "Kannada", "Punjabi")

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
                    contentPadding = PaddingValues(bottom = 130.dp)
                ) {
                    // 1. Top App Header: "Musync" Title + Glass Search Bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Musync",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = TextWhite
                            )

                            // Glass Search Pill
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 150.dp, max = 210.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x351E222D))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = IconGrey,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    androidx.compose.foundation.text.BasicTextField(
                                        value = uiState.searchQuery,
                                        onValueChange = { viewModel.onSearchQueryChange(it) },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontSize = 12.sp
                                        ),
                                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                                        modifier = Modifier.weight(1f),
                                        decorationBox = { innerTextField ->
                                            if (uiState.searchQuery.isEmpty()) {
                                                Text(
                                                    text = if (uiState.isOffline) "Search local..." else "Search...",
                                                    color = TextGreyMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            innerTextField()
                                        }
                                    )
                                    if (uiState.searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.clearSearch() },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Glassmorphic Language Filter Chips
                    if (!uiState.isOffline && uiState.searchQuery.isBlank()) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(languagePills, key = { it }) { lang ->
                                    val isSelected = uiState.selectedLanguage == lang
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(if (isSelected) Color(0x55FFFFFF) else Color(0x201E222D))
                                            .border(1.dp, if (isSelected) Color.White else Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                                            .clickable { viewModel.selectLanguage(lang) }
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = lang,
                                            color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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
                                    onAddToQueue = { viewModel.addToQueue(track) }
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
                                    onAddToQueue = { viewModel.addToQueue(track) }
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
                                    items(uiState.teluguTracks, key = { it.id }) { track ->
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
                                    items(uiState.tamilTracks, key = { it.id }) { track ->
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
                                    items(uiState.hindiTracks, key = { it.id }) { track ->
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
                                    items(uiState.globalTracks, key = { it.id }) { track ->
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

                            items(uiState.trendingTracks, key = { it.id }) { track ->
                                TrackItem(
                                    track = track,
                                    isPlaying = playbackState.currentTrack?.id == track.id,
                                    isFavorite = favoriteIds.contains(track.id),
                                    onClick = { viewModel.playTrack(track, uiState.trendingTracks) },
                                    onFavoriteToggle = { viewModel.toggleFavorite(track) },
                                    onPlayNext = { viewModel.playNext(track) },
                                    onAddToQueue = { viewModel.addToQueue(track) }
                                )
                            }
                        }
                    }
                }
            }
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
    val imageRequest = androidx.compose.runtime.remember(track.artworkUrl) {
        ImageRequest.Builder(context)
            .data(track.artworkUrl)
            .crossfade(true)
            .size(240, 240)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
    }

    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x351E222D))
            .border(1.dp, if (isPlaying) StatusGreen else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        // Song Artwork Container
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x20FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
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

            // Glass Play Circle Button Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) StatusGreen else Color(0x80000000))
                    .border(1.dp, Color(0x44FFFFFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = if (isPlaying) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Song Title
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = if (isPlaying) StatusGreen else TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        // Artist Name
        Text(
            text = track.artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextGreySecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

