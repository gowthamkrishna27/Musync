package com.musync.app.ui.browse

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.MusyncApplication
import com.musync.app.domain.model.Track
import com.musync.app.ui.components.AddToPlaylistDialog
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.home.HomeViewModel
import com.musync.app.ui.theme.AppleMusicRed
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MusyncApplication
    val playlistRepository = app.container.playlistRepository
    val uiState by viewModel.uiState.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()

    var trackForPlaylist by remember { mutableStateOf<Track?>(null) }
    val favoriteIds = favorites.map { it.id }.toSet()

    val featuredBanners = listOf(
        FeaturedBanner("New Music Daily", "Fresh drops & chart-topping releases", listOf(Color(0xFFFA2D48), Color(0xFFFF6B6B)), "DAILY ESSENTIAL"),
        FeaturedBanner("Spatial Audio Spotlight", "Immersive 3D sound in Dolby Atmos", listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121)), "SPATIAL AUDIO"),
        FeaturedBanner("Global Top 100", "The most played tracks worldwide", listOf(Color(0xFF00B4DB), Color(0xFF0083B0)), "TOP CHARTS"),
        FeaturedBanner("A-List Pop & Dance", "High-energy hits from premier artists", listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0)), "FEATURED")
    )

    val genreChips = listOf("All", "Pop", "Hip-Hop", "Electronic", "R&B", "Rock", "Bollywood", "Tollywood", "Acoustic")
    var selectedGenre by remember { mutableStateOf("All") }

    PullToRefreshBox(
        isRefreshing = uiState.isLoading,
        onRefresh = { viewModel.loadHomeData() },
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 160.dp)
        ) {
            // 1. Header: Logo + "New"
            item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.musync.app.R.drawable.ic_musync_logo),
                        contentDescription = "Musync Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "New",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = TextWhite
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Discover latest drops, albums & curated charts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGreySecondary
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // 2. Genre Filter Pills
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(genreChips) { genre ->
                    val isSelected = selectedGenre == genre
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) AppleMusicRed else Color(0xFF1E1E22))
                            .border(1.dp, if (isSelected) AppleMusicRed else Color(0x22FFFFFF), RoundedCornerShape(20.dp))
                            .clickable { selectedGenre = genre }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = genre,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Featured Showcase Carousel
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(featuredBanners) { banner ->
                    Box(
                        modifier = Modifier
                            .width(280.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(banner.colors))
                            .clickable {
                                uiState.trendingTracks.firstOrNull()?.let {
                                    viewModel.playTrack(it, uiState.trendingTracks)
                                }
                            }
                            .padding(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x35000000))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = banner.badge,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = banner.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = banner.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xDDFFFFFF)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 4. Latest Songs (Apple Music 3-Row Grid or List)
        item {
            SectionHeader(
                title = "New Tracks & Singles",
                actionText = "Play all",
                onActionClick = {
                    uiState.trendingTracks.firstOrNull()?.let {
                        viewModel.playTrack(it, uiState.trendingTracks)
                    }
                }
            )
        }

        itemsIndexed(uiState.trendingTracks.take(15), key = { index, track -> "new_${track.id}_$index" }) { _, track ->
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

    val currentTrackForPlaylist = trackForPlaylist
    if (currentTrackForPlaylist != null) {
        AddToPlaylistDialog(
            track = currentTrackForPlaylist,
            playlistRepository = playlistRepository,
            onDismiss = { trackForPlaylist = null }
        )
    }
}

private data class FeaturedBanner(
    val title: String,
    val subtitle: String,
    val colors: List<Color>,
    val badge: String
)

