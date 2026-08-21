package com.musync.app.ui.offline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musync.app.MusyncApplication
import com.musync.app.R
import com.musync.app.domain.model.Track
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.AppleMusicPink
import com.musync.app.ui.theme.AppleMusicRed
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.GlassBackground
import com.musync.app.ui.theme.GlassBorder
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MusyncApplication
    val downloadRepo = app.container.downloadRepository
    val downloadManager = app.container.musyncDownloadManager
    val playbackManager = app.container.playbackManager

    val downloadedTracks by downloadRepo.getDownloadedTracks().collectAsState(initial = emptyList())
    val totalStorageBytes by downloadRepo.getTotalStorageUsed().collectAsState(initial = 0L)
    val activeDownloads by downloadManager.downloadStates.collectAsState()
    val playbackState by playbackManager.playbackState.collectAsState()

    val storageFormatted = formatBytes(totalStorageBytes ?: 0L)
    val pendingDownloads = activeDownloads.values.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 160.dp)
    ) {
        // 1. Header: Logo + "Offline"
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_musync_logo),
                        contentDescription = "Musync Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        ),
                        color = TextWhite
                    )
                }

                if (downloadedTracks.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x2230D158))
                            .border(1.dp, Color(0x5530D158), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Offline ready",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Available Offline",
                                color = Color(0xFF30D158),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // 2. Storage & Quick Action Showcase Banner
        if (downloadedTracks.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF2A1526),
                                    Color(0xFF161622)
                                )
                            )
                        )
                        .border(1.dp, Color(0x33FA2D48), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${downloadedTracks.size} Songs Available Offline",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$storageFormatted used on device • High Quality MP4",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGreySecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (downloadedTracks.isNotEmpty()) {
                                        playbackManager.playTracks(downloadedTracks, 0)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.Bold, color = Color.White)
                            }

                            Button(
                                onClick = {
                                    if (downloadedTracks.isNotEmpty()) {
                                        val shuffled = downloadedTracks.shuffled()
                                        playbackManager.playTracks(shuffled, 0)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282830))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 3. Active Downloading Queue Section
        if (pendingDownloads.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(
                        text = "Downloading (${pendingDownloads.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AppleMusicPink
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(pendingDownloads, key = { "dl_${it.trackId}" }) { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1C1C22))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.trackId,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (item.status == "QUEUED") "Queued..." else "${(item.progress * 100).toInt()}% • ${formatBytes(item.bytesDownloaded)} of ${formatBytes(item.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGreySecondary
                                )
                            }
                            IconButton(onClick = { downloadManager.cancel(item.trackId) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel",
                                    tint = TextGreyMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { item.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = AppleMusicPink,
                            trackColor = Color(0x33FFFFFF)
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(14.dp)) }
        }

        // 4. Downloaded Songs List or Empty State
        if (downloadedTracks.isEmpty() && pendingDownloads.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E24)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DownloadForOffline,
                                contentDescription = null,
                                tint = TextGreyMuted,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Offline Music",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            ),
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Songs you download will appear here for playback anywhere, without using mobile data or Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextGreySecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            }
        } else if (downloadedTracks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Downloaded Songs",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = TextWhite
                    )
                }
            }

            items(downloadedTracks, key = { "offline_${it.id}" }) { track ->
                val isPlaying = playbackState.currentTrack?.id == track.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val index = downloadedTracks.indexOfFirst { it.id == track.id }
                            playbackManager.playTracks(downloadedTracks, if (index >= 0) index else 0)
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artwork
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF222228))
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isPlaying) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x66000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AppleMusicPink,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Title & Artist
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = if (isPlaying) AppleMusicPink else TextWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Offline",
                                tint = Color(0xFF30D158),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = track.artist.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreySecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Delete action
                    IconButton(
                        onClick = { downloadManager.delete(track.id) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete download",
                            tint = TextGreyMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.2f GB", gb)
        mb >= 1.0 -> String.format("%.1f MB", mb)
        else -> String.format("%.0f KB", kb)
    }
}
