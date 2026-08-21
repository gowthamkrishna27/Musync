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
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.MusyncApplication
import com.musync.app.R
import com.musync.app.domain.model.Track
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.AppleMusicPink
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OfflineFilterPill {
    ALL, DOWNLOADS, DEVICE, QUEUE
}

@Composable
fun OfflineScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MusyncApplication
    val scope = rememberCoroutineScope()
    val downloadRepo = app.container.downloadRepository
    val downloadManager = app.container.musyncDownloadManager
    val playbackManager = app.container.playbackManager
    val localScanner = app.container.localAudioScanner
    val favoritesRepo = app.container.favoritesRepository

    val downloadedTracks by downloadRepo.getDownloadedTracks().collectAsState(initial = emptyList())
    val totalStorageBytes by downloadRepo.getTotalStorageUsed().collectAsState(initial = 0L)
    val activeDownloads by downloadManager.downloadStates.collectAsState()
    val playbackState by playbackManager.playbackState.collectAsState()
    val favorites by favoritesRepo.getFavorites().collectAsState(initial = emptyList())
    val favoriteIds = remember(favorites) { favorites.map { it.id }.toSet() }

    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(OfflineFilterPill.ALL) }
    var hasStoragePermission by remember { mutableStateOf(localScanner.hasStoragePermission()) }

    LaunchedEffect(hasStoragePermission) {
        if (hasStoragePermission) {
            withContext(Dispatchers.IO) {
                localTracks = localScanner.scanLocalAudio()
            }
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
        if (isGranted) {
            scope.launch(Dispatchers.IO) {
                localTracks = localScanner.scanLocalAudio()
            }
        }
    }

    val storageFormatted = formatBytes(totalStorageBytes ?: 0L)
    val pendingDownloads = activeDownloads.values.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" }
    val allOfflineTracks = remember(downloadedTracks, localTracks) { (downloadedTracks + localTracks).distinctBy { it.id } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 160.dp)
    ) {
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
                    Image(
                        painter = painterResource(id = R.drawable.ic_musync_logo),
                        contentDescription = "Musync Logo",
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Offline",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = TextWhite
                    )
                }

                if (downloadedTracks.isNotEmpty() || localTracks.isNotEmpty()) {
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
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "${allOfflineTracks.size} songs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF30D158)
                            )
                        }
                    }
                }
            }
        }

        item {
            val pills = mutableListOf(
                OfflineFilterPill.ALL to "All Offline (${allOfflineTracks.size})",
                OfflineFilterPill.DOWNLOADS to "Downloads (${downloadedTracks.size})",
                OfflineFilterPill.DEVICE to "Device Audio (${localTracks.size})"
            )
            if (pendingDownloads.isNotEmpty()) {
                pills.add(OfflineFilterPill.QUEUE to "Queue (${pendingDownloads.size})")
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pills, key = { it.first }) { (pill, label) ->
                    val isSelected = selectedFilter == pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Color(0xFF2E2E36) else Color(0xFF1B1B20))
                            .border(1.dp, if (isSelected) Color(0x66FFFFFF) else Color(0x1AFFFFFF), RoundedCornerShape(20.dp))
                            .clickable { selectedFilter = pill }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFB0B0B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (allOfflineTracks.isNotEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF22242D),
                                    Color(0xFF17181F),
                                    Color(0xFF111116)
                                )
                            )
                        )
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Ready For Playback Anywhere",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    ),
                                    color = TextWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${downloadedTracks.size} downloaded ($storageFormatted) • ${localTracks.size} on device",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                    color = TextGreySecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val tracksToPlay = when (selectedFilter) {
                                        OfflineFilterPill.DOWNLOADS -> downloadedTracks
                                        OfflineFilterPill.DEVICE -> localTracks
                                        else -> allOfflineTracks
                                    }
                                    if (tracksToPlay.isNotEmpty()) {
                                        playbackManager.playTracks(tracksToPlay, 0)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Button(
                                onClick = {
                                    val tracksToPlay = when (selectedFilter) {
                                        OfflineFilterPill.DOWNLOADS -> downloadedTracks
                                        OfflineFilterPill.DEVICE -> localTracks
                                        else -> allOfflineTracks
                                    }
                                    if (tracksToPlay.isNotEmpty()) {
                                        val shuffled = tracksToPlay.shuffled()
                                        playbackManager.playTracks(shuffled, 0)
                                    }
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282830)),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF))
                            ) {
                                Icon(Icons.Default.Shuffle, contentDescription = null, tint = TextWhite, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Shuffle", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextWhite)
                            }
                        }
                    }
                }
            }
        }

        // 4. Live Active Downloads Queue
        if (pendingDownloads.isNotEmpty()) {
            item {
                SectionHeader(title = "Downloading (${pendingDownloads.size})")
            }
            items(pendingDownloads, key = { it.trackId }) { dl ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1C1D24))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            progress = { dl.progress },
                            modifier = Modifier.size(32.dp),
                            color = AppleMusicPink,
                            strokeWidth = 3.dp,
                            trackColor = Color(0x33FFFFFF)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dl.trackId,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val pct = (dl.progress * 100).toInt()
                            val loadedMb = String.format("%.1f", dl.bytesDownloaded / (1024.0 * 1024.0))
                            val totalMb = if (dl.totalBytes > 0) String.format("%.1f", dl.totalBytes / (1024.0 * 1024.0)) else "?"
                            Text(
                                text = "$loadedMb / $totalMb MB ($pct%)",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = TextGreySecondary
                            )
                        }
                        IconButton(onClick = { downloadManager.cancel(dl.trackId) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = TextGreyMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 5. Filtered Tracks Section
        when (selectedFilter) {
            OfflineFilterPill.ALL -> {
                // Section A: Downloaded Tracks
                if (downloadedTracks.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Downloaded MP4 Music (${downloadedTracks.size})")
                    }
                    items(downloadedTracks, key = { "dl_${it.id}" }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            subtitleExtra = "Offline MP4",
                            trailingContent = {
                                IconButton(onClick = { scope.launch { downloadRepo.deleteDownload(track.id) } }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Download",
                                        tint = TextGreyMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            onClick = {
                                val idx = allOfflineTracks.indexOfFirst { it.id == track.id }
                                playbackManager.playTracks(allOfflineTracks, if (idx >= 0) idx else 0)
                            },
                            onFavoriteToggle = { scope.launch { favoritesRepo.toggleFavorite(track) } },
                            onPlayNext = { playbackManager.playNext(track) },
                            onAddToQueue = { playbackManager.addToQueue(track) }
                        )
                    }
                }

                // Section B: Device Scanned Music
                if (localTracks.isNotEmpty()) {
                    item {
                        SectionHeader(title = "Device Audio (${localTracks.size})")
                    }
                    items(localTracks, key = { "loc_${it.id}" }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            subtitleExtra = "On Device",
                            onClick = {
                                val idx = allOfflineTracks.indexOfFirst { it.id == track.id }
                                playbackManager.playTracks(allOfflineTracks, if (idx >= 0) idx else 0)
                            },
                            onFavoriteToggle = { scope.launch { favoritesRepo.toggleFavorite(track) } },
                            onPlayNext = { playbackManager.playNext(track) },
                            onAddToQueue = { playbackManager.addToQueue(track) }
                        )
                    }
                }
            }

            OfflineFilterPill.DOWNLOADS -> {
                if (downloadedTracks.isEmpty()) {
                    item {
                        OfflineEmptyState(
                            title = "No Downloaded Music",
                            description = "Tap the download icon on any song or in the player to save it for offline listening."
                        )
                    }
                } else {
                    items(downloadedTracks, key = { "dl_only_${it.id}" }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            subtitleExtra = "Offline MP4",
                            trailingContent = {
                                IconButton(onClick = { scope.launch { downloadRepo.deleteDownload(track.id) } }) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Download",
                                        tint = TextGreyMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            onClick = {
                                val idx = downloadedTracks.indexOfFirst { it.id == track.id }
                                playbackManager.playTracks(downloadedTracks, if (idx >= 0) idx else 0)
                            },
                            onFavoriteToggle = { scope.launch { favoritesRepo.toggleFavorite(track) } },
                            onPlayNext = { playbackManager.playNext(track) },
                            onAddToQueue = { playbackManager.addToQueue(track) }
                        )
                    }
                }
            }

            OfflineFilterPill.DEVICE -> {
                if (!hasStoragePermission) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = TextGreyMuted, modifier = Modifier.size(44.dp))
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(text = "Permission Required", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextWhite)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Grant storage access to play audio files already stored on your phone.",
                                    fontSize = 12.sp,
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
                                    colors = ButtonDefaults.buttonColors(containerColor = AppleMusicPink),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Grant Storage Access", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (localTracks.isEmpty()) {
                    item {
                        OfflineEmptyState(
                            title = "No Audio Files Found",
                            description = "No local audio files were found in your phone storage."
                        )
                    }
                } else {
                    items(localTracks, key = { "dev_only_${it.id}" }) { track ->
                        TrackItem(
                            track = track,
                            isPlaying = playbackState.currentTrack?.id == track.id,
                            isFavorite = favoriteIds.contains(track.id),
                            subtitleExtra = "On Device",
                            onClick = {
                                val idx = localTracks.indexOfFirst { it.id == track.id }
                                playbackManager.playTracks(localTracks, if (idx >= 0) idx else 0)
                            },
                            onFavoriteToggle = { scope.launch { favoritesRepo.toggleFavorite(track) } },
                            onPlayNext = { playbackManager.playNext(track) },
                            onAddToQueue = { playbackManager.addToQueue(track) }
                        )
                    }
                }
            }

            OfflineFilterPill.QUEUE -> {
                if (pendingDownloads.isEmpty()) {
                    item {
                        OfflineEmptyState(
                            title = "Download Queue Empty",
                            description = "All downloads have completed."
                        )
                    }
                }
            }
        }

        // Global Empty State if completely empty
        if (allOfflineTracks.isEmpty() && pendingDownloads.isEmpty() && hasStoragePermission) {
            item {
                OfflineEmptyState(
                    title = "Your Offline Music Hub",
                    description = "Download songs while online to listen anywhere with zero data usage, or play device audio files."
                )
            }
        }
    }
}

@Composable
private fun OfflineEmptyState(title: String, description: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = null,
                tint = TextGreyMuted,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 18.sp),
                color = TextGreyMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.1f GB", mb / 1024.0)
    } else {
        String.format("%.0f MB", mb)
    }
}
