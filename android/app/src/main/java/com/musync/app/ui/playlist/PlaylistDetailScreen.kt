package com.musync.app.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.CardElevated
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

import androidx.compose.foundation.layout.statusBarsPadding

import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.MoreVert

@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playlist by viewModel.playlist.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val playbackState by viewModel.playbackManager.playbackState.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }

    val favoriteIds = favorites.map { it.id }.toSet()
    val tracks = playlist?.tracks ?: emptyList()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // Ambient background subtle glow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x55FA2D48),
                            Color(0x22181A24),
                            BackgroundBlack
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 8.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = IconWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Playlist",
                        tint = IconGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                // Large Artwork Header
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color(0xFF1C1C1E))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val firstArtwork = tracks.firstOrNull()?.artworkUrl
                            if (!firstArtwork.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = firstArtwork,
                                    contentDescription = playlist?.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(64.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = playlist?.name ?: "Playlist",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = TextWhite,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${tracks.size} songs • Updated recently",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                            color = TextGreySecondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Dual Play & Shuffle Buttons (Apple Music style)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 1. Play Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color.White)
                                    .clickable {
                                        if (tracks.isNotEmpty()) {
                                            viewModel.playAll()
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            // 2. Shuffle Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFF242429))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                                    .clickable {
                                        if (tracks.isNotEmpty()) {
                                            val shuffled = tracks.shuffled()
                                            viewModel.playTrack(shuffled.first(), shuffled)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = com.musync.app.ui.theme.AppleMusicRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shuffle",
                                        color = com.musync.app.ui.theme.AppleMusicRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Track List
                if (tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks in this playlist yet.\nAdd songs from Home or Search!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextGreyMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { index, track -> "pl_${track.id}_$index" }) { _, track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            onClick = { viewModel.playTrack(track, tracks) },
                            onFavoriteToggle = { viewModel.toggleFavorite(track) },
                            onPlayNext = { viewModel.playbackManager.playNext(track) },
                            onAddToQueue = { viewModel.playbackManager.addToQueue(track) },
                            onRemove = { viewModel.removeTrack(track.id) }
                        )
                    }
                }
            }
        }
    }


    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${playlist?.name ?: "this playlist"}\"? This action cannot be undone.",
                    color = TextGreySecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE53935))
                        .clickable {
                            showDeleteDialog = false
                            viewModel.deletePlaylist {
                                onNavigateBack()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showDeleteDialog = false }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                }
            },
            containerColor = com.musync.app.ui.theme.SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }
}

