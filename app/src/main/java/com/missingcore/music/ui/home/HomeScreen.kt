package com.missingcore.music.ui.home

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.missingcore.music.domain.model.Track
import com.missingcore.music.ui.components.ErrorView
import com.missingcore.music.ui.components.LoadingView
import com.missingcore.music.ui.components.SectionHeader
import com.missingcore.music.ui.components.TrackItem
import com.missingcore.music.ui.theme.BackgroundBlack
import com.missingcore.music.ui.theme.CardElevated
import com.missingcore.music.ui.theme.IconGrey
import com.missingcore.music.ui.theme.TextGreyMuted
import com.missingcore.music.ui.theme.TextGreySecondary
import com.missingcore.music.ui.theme.TextWhite

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
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
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    // Top App Header: "Musync" Title + Right Top Rounded Search Bar
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
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

                            // Sleek Top-Right Rounded Search Pill
                            Box(
                                modifier = Modifier
                                    .widthIn(min = 150.dp, max = 210.dp)
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x2E252836))
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

                    // Offline Status Banner
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
                                            imageVector = androidx.compose.material.icons.Icons.Default.Warning,
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

                    // Search Results Mode (When query is present)
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
                            items(uiState.searchResults) { track ->
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
                        // Offline Mode: Automatically Show Local Device Tracks
                        if (uiState.localTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Local Device Tracks (${uiState.localTracks.size})",
                                    actionText = "Refresh",
                                    onActionClick = { viewModel.loadHomeData() }
                                )
                            }
                            items(uiState.localTracks) { track ->
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
                        } else {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "No Local Music Found",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = TextWhite
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Add audio files to your device storage to play music offline.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextGreyMuted,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Online Home Content: Section 1: "Top Telugu Hits 2026" Horizontal Row
                        val horizontalTracks = if (uiState.trendingTracks.isNotEmpty()) {
                            uiState.trendingTracks.take(8)
                        } else emptyList()

                        if (horizontalTracks.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Top Telugu Hits 2026",
                                    actionText = "See all",
                                    onActionClick = { }
                                )

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(horizontalTracks) { track ->
                                        HomeRecentlyPlayedCard(
                                            track = track,
                                            onClick = { viewModel.playTrack(track, horizontalTracks) }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }

                        // Section 2: "Trending Telugu Songs" Vertical List
                        item {
                            SectionHeader(
                                title = "Trending Telugu Songs",
                                actionText = "See all",
                                onActionClick = { }
                            )
                        }

                        items(uiState.trendingTracks) { track ->
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

@Composable
private fun HomeRecentlyPlayedCard(
    track: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(116.dp)
            .clickable(onClick = onClick)
    ) {
        // Square Album Cover (116dp x 116dp, rounded 12dp)
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (!track.artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = track.artworkUrl,
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            ),
            color = TextWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist.name,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = TextGreySecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
