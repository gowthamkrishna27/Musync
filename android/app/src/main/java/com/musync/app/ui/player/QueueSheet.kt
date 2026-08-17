package com.musync.app.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.domain.model.PlaybackState
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.components.TrackItem
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    playbackState: PlaybackState,
    onDismiss: () -> Unit,
    onPlayTrackAtIndex: (Int) -> Unit,
    onRemoveFromQueue: (String) -> Unit,
    onClearQueue: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as com.musync.app.MusyncApplication
    val recommendationViewModel: RecommendationViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = RecommendationViewModel.Factory(app.container.playbackManager, app.container.musicRepository)
    )
    val recUiState by recommendationViewModel.uiState.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBlack)
                .padding(top = 12.dp)
        ) {
            // Header: Close "✕", "Queue", "Edit"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = IconWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = TextWhite
                )

                TextButton(onClick = onClearQueue) {
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextGreySecondary
                    )
                }
            }

            if (playbackState.queue.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "The queue is currently empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextGreyMuted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Section: Now Playing
                    playbackState.currentTrack?.let { currentTrack ->
                        item {
                            SectionHeader(title = "Now Playing")
                            TrackItem(
                                track = currentTrack,
                                isPlaying = true,
                                isFavorite = false,
                                trailingContent = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Equalizer,
                                            contentDescription = "Now Playing",
                                            tint = IconWhite,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Reorder",
                                            tint = IconGrey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                onClick = { },
                                onRemove = null
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }

                    // Section: Up Next
                    val nextTracks = playbackState.queue.filterIndexed { index, _ ->
                        index > playbackState.queueIndex
                    }

                    item {
                        SectionHeader(title = "Up Next")
                    }

                    if (nextTracks.isEmpty()) {
                        item {
                            Text(
                                text = "End of queue. Next songs will repeat according to repeat mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreyMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        itemsIndexed(playbackState.queue) { index, track ->
                            if (index > playbackState.queueIndex) {
                                TrackItem(
                                    track = track,
                                    isPlaying = false,
                                    isFavorite = false,
                                    trailingContent = {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = "Reorder",
                                            tint = IconGrey,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = { onPlayTrackAtIndex(index) },
                                    onRemove = { onRemoveFromQueue(track.id) }
                                )
                            }
                        }
                    }

                    // Section: You May Also Like
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        RecommendationSection(
                            uiState = recUiState,
                            onTrackClick = { recommendedTrack ->
                                app.container.playbackManager.play(recommendedTrack)
                                onDismiss()
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

