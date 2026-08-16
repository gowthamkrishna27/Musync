package com.musync.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.musync.app.domain.model.Track
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.CardElevated
import com.musync.app.ui.theme.DeleteRed
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconMuted
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite
import androidx.compose.material.icons.filled.Videocam

@Composable
fun TrackItem(
    track: Track,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    subtitleExtra: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onFavoriteToggle: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onPlayVideo: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .then(
                if (isPlaying) {
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(Color(0x28FFFFFF), Color(0x10FFFFFF), Color.Transparent)
                            )
                        )
                        .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(12.dp))
                } else Modifier
            )
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Square Album Artwork (48dp x 48dp, rounded 8dp)
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardElevated),
            contentAlignment = Alignment.Center
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val imageRequest = remember(track.artworkUrl, track.id) {
                com.musync.app.util.ImageQualityHelper.buildOptimizedImageRequest(context, track.artworkUrl, track.id)
            }

            if (!track.artworkUrl.isNullOrBlank() || track.id.isNotBlank()) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                DefaultArtworkView(
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 22.dp,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist Row
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium
                ),
                color = TextWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.artist.name,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = TextGreySecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (subtitleExtra != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = subtitleExtra,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextGreyMuted
                    )
                } else if (!track.genre.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = track.genre,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = TextGreyMuted,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Custom trailing content or 3-dots / heart menu
        if (trailingContent != null) {
            trailingContent()
        } else {
            if (onFavoriteToggle != null) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onFavoriteToggle()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) IconWhite else IconMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = IconGrey,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(SurfaceBlack)
                ) {
                    if (onPlayVideo != null) {
                        DropdownMenuItem(
                            text = { Text("Watch Video", color = com.musync.app.ui.theme.TextWhite, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Videocam, null, tint = IconGrey, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onPlayVideo()
                            }
                        )
                    }
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Play Next", color = com.musync.app.ui.theme.TextWhite, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.SkipNext, null, tint = IconGrey, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onPlayNext()
                            }
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Queue", color = TextWhite, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = IconGrey, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAddToQueue()
                            }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = TextWhite, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = IconGrey, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (onRemove != null) {
                        DropdownMenuItem(
                            text = { Text("Remove", color = DeleteRed, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = DeleteRed, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
}

