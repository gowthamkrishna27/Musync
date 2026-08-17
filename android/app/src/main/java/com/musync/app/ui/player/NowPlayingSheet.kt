package com.musync.app.ui.player

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import com.musync.app.ui.components.AddToPlaylistDialog
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.musync.app.domain.model.PlaybackState
import com.musync.app.domain.model.RepeatMode
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.CardElevated
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.IconMuted
import com.musync.app.ui.theme.IconWhite
import com.musync.app.ui.theme.StatusGreen
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val track = playbackState.currentTrack ?: return
    val context = LocalContext.current
    val app = context.applicationContext as com.musync.app.MusyncApplication
    val playbackManager = app.container.playbackManager
    val audioEffectManager = app.container.audioEffectManager
    val eqState by audioEffectManager.state.collectAsState()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showAuthSheetInPlayer by remember { mutableStateOf(false) }

    var selectedEqPreset by remember { mutableStateOf("Bass Boost") }
    var bassLevel by remember { mutableFloatStateOf(0.7f) }
    var midLevel by remember { mutableFloatStateOf(0.5f) }
    var trebleLevel by remember { mutableFloatStateOf(0.6f) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundBlack,
        shape = androidx.compose.ui.graphics.RectangleShape,
        dragHandle = null
    ) {
        val playingFromText = remember(track) {
            when {
                track.id.startsWith("local") -> "Device Audio"
                !track.genre.isNullOrBlank() && track.genre != "Music" -> track.genre
                else -> "${track.artist.name} Radio"
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBlack)
        ) {
            // 1. FULL-BLEED EDGE-TO-EDGE ATMOSPHERIC AMBIENT BACKGROUND
            AtmosphericBackground(
                artworkUrl = track.artworkUrl,
                trackId = track.id,
                isPlaying = playbackState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )

            // 2. EXISTING PLAYER UI (Interactive Foreground Layer)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .padding(horizontal = 24.dp)
                    .padding(top = 48.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Header: Down Arrow, "PLAYING FROM", Video Atmosphere Toggle, 3-dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = IconWhite,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 1.sp,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = TextGreyMuted
                        )
                        Text(
                            text = playingFromText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextWhite,
                            maxLines = 1
                        )
                    }

                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = IconWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(0.6f))

            // 2. Large Centered Album Artwork Banner with Round Corners & Transparent Glass Halo
            val infiniteTransition = rememberInfiniteTransition(label = "glowAnimation")
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3200, easing = FastOutSlowInEasing),
                    repeatMode = AnimRepeatMode.Reverse
                ),
                label = "glowScale"
            )
            val glowAlpha by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.55f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3200, easing = FastOutSlowInEasing),
                    repeatMode = AnimRepeatMode.Reverse
                ),
                label = "glowAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Ambient Frosted Transparent Glass Halo
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .scale(if (playbackState.isPlaying) glowScale else 1f)
                        .graphicsLayer(alpha = if (playbackState.isPlaying) glowAlpha else 0.20f)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0x40FFFFFF), // Pure Translucent Frosted Glass Glow
                                    Color(0x18FFFFFF),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Large Main Album Artwork Banner with Round Corners
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.96f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(CardElevated)
                        .border(1.5.dp, Color(0x33FFFFFF), RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val rawUrl = track.artworkUrl
                    val imageRequest = com.musync.app.core.image.ImageQualityHelper.buildOptimizedImageRequest(
                        context = LocalContext.current,
                        url = rawUrl,
                        videoId = track.id
                    )

                    if (!rawUrl.isNullOrBlank() || track.id.isNotBlank()) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        com.musync.app.ui.components.DefaultArtworkView(
                            modifier = Modifier.fillMaxSize(),
                            iconSize = 64.dp,
                            shape = RoundedCornerShape(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.7f))

            // 3. Track Info (Title, Artist, Heart Toggle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = TextWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artist.name,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = TextGreySecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onToggleFavorite()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = IconWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Progress Slider Section (Track, Thumb, Time Labels)
            Column(modifier = Modifier.fillMaxWidth()) {
                val duration = playbackState.durationMs.coerceAtLeast(1L)
                val currentPos = if (isDraggingSlider) {
                    (sliderDragPosition * duration).toLong()
                } else {
                    playbackState.currentPositionMs.coerceIn(0L, duration)
                }
                val progressRatio = (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newRatio = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetMs = (newRatio * duration).toLong()
                                onSeekTo(targetMs)
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDraggingSlider = true
                                    sliderDragPosition = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    val targetMs = (sliderDragPosition * duration).toLong()
                                    onSeekTo(targetMs)
                                    isDraggingSlider = false
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                },
                                onDragCancel = {
                                    isDraggingSlider = false
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val newRatio = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    sliderDragPosition = newRatio
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            )
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val totalWidth = maxWidth
                    val thumbOffset = (totalWidth - 12.dp) * progressRatio

                    // Inactive Background Track
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2E2E32))
                    )

                    // Active Progress Fill Track
                    Box(
                        modifier = Modifier
                            .width(totalWidth * progressRatio)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White)
                    )

                    // Clean Round White Thumb
                    Box(
                        modifier = Modifier
                            .padding(start = thumbOffset.coerceAtLeast(0.dp))
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Time Labels (0:48 / 2:26)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPos),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextGreyMuted
                    )
                    Text(
                        text = formatTime(duration),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextGreyMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Controls Row (Shuffle, Previous, Play/Pause Button, Next, Repeat)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggleShuffle()
                }) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.isShuffle) IconWhite else IconMuted,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSkipPrevious()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        tint = IconWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Play / Pause Large Dark Glass Button
                if (playbackState.isBuffering) {
                    CircularProgressIndicator(
                        color = TextWhite,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(62.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF282C37))
                            .border(1.5.dp, Color(0x55FFFFFF), CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                onTogglePlay()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSkipNext()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        tint = IconWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onToggleRepeat()
                }) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> Icons.Default.RepeatOne
                            else -> Icons.Default.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = when (playbackState.repeatMode) {
                            RepeatMode.OFF -> IconMuted
                            else -> IconWhite
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Bottom Tool Row (Queue, Add to Playlist, Equalizer/Tune, Devices)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onOpenQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = IconGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { showAddToPlaylistDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        tint = IconGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { showEqualizerSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Equalizer",
                        tint = if (showEqualizerSheet) IconWhite else IconGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(onClick = { showDeviceDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Devices,
                        contentDescription = "Devices",
                        tint = if (showDeviceDialog) IconWhite else IconGrey,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

    // Add to Playlist Dialog
    if (showAddToPlaylistDialog) {
        val app = context.applicationContext as com.musync.app.MusyncApplication
        AddToPlaylistDialog(
            track = track,
            playlistRepository = app.container.playlistRepository,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    // 1. Dolby Atmos Modal Bottom Sheet
    if (showEqualizerSheet) {
        val authManager = app.container.authManager
        val currentUser by authManager.currentUser.collectAsState()
        val isLoggedIn = currentUser != null && !currentUser!!.isAnonymous
        val eqSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        var isEngineDropdownExpanded by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            sheetState = eqSheetState,
            containerColor = Color(0xF212131A),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x40FFFFFF))
                )
            },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                // Header + Master Toggle (Translucent Dark Glass)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = isLoggedIn) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            audioEffectManager.setEnabled(!eqState.isEnabled)
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.musync.app.ui.components.SoundEngineSymbol(
                                engineId = eqState.currentEngine.id,
                                tint = if (isLoggedIn && eqState.isEnabled) Color.White else IconGrey,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Studio & Spatial Audio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Text(
                                text = if (!isLoggedIn) "Member Exclusive" else if (eqState.isEnabled) "DSP Engine Active" else "Bypassed",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (!isLoggedIn) Color(0xBBFFFFFF) else if (eqState.isEnabled) Color.White else TextGreyMuted
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isLoggedIn) {
                            Switch(
                                checked = eqState.isEnabled,
                                onCheckedChange = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    audioEffectManager.setEnabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = Color.White,
                                    uncheckedThumbColor = TextGreyMuted,
                                    uncheckedTrackColor = Color(0x22FFFFFF)
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .clickable { showEqualizerSheet = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                if (!isLoggedIn) {
                    // Translucent Dark Glass Lock Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x28FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Member Exclusive Sound",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sign in to unlock all 7 spatial engines, custom hardware DSP, and immersive 3D surround sound.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGreySecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 17.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showEqualizerSheet = false
                                    showAuthSheetInPlayer = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Sign In to Unlock", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    // Direct 7 Actual Sound Engines
                    Text(
                        text = "SOUND ENGINES",
                        color = Color(0x88FFFFFF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val signatureEngines = com.musync.app.playback.SoundEngineRegistry.signatureEngines

                    // 7 Engine Square Buttons Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        signatureEngines.forEach { engineMode ->
                            val isEngineSelected = eqState.currentEngine == engineMode.engine

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isEngineSelected && eqState.isEnabled) Color(0x38FFFFFF) else Color(0x14FFFFFF)
                                    )
                                    .border(
                                        if (isEngineSelected && eqState.isEnabled) 1.5.dp else 1.dp,
                                        if (isEngineSelected && eqState.isEnabled) Color.White else Color(0x24FFFFFF),
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        audioEffectManager.setEngineMode(engineMode)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                com.musync.app.ui.components.SoundEngineSymbol(
                                    engineId = engineMode.engine.id,
                                    tint = if (isEngineSelected && eqState.isEnabled) Color.White else Color(0x77FFFFFF),
                                    modifier = Modifier.size(24.dp)
                                )

                                if (isEngineSelected && eqState.isEnabled) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Engine & Signature Mode Info Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x18FFFFFF))
                            .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0x24FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.musync.app.ui.components.SoundEngineSymbol(
                                        engineId = eqState.currentEngine.id,
                                        tint = TextWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = eqState.currentMode.title,
                                        color = TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = eqState.currentMode.subtitle,
                                        color = Color(0x99FFFFFF),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            val modeTag = eqState.currentMode.recommendationTag
                            if (modeTag != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x22FFFFFF))
                                        .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = modeTag,
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.3.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // High-contrast, always visible glossy Done Button
                Button(
                    onClick = { showEqualizerSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    if (showAuthSheetInPlayer) {
        val app = context.applicationContext as com.musync.app.MusyncApplication
        com.musync.app.ui.auth.AuthBottomSheet(
            authManager = app.container.authManager,
            onDismiss = { showAuthSheetInPlayer = false }
        )
    }

    // 2. Audio Device Dialog
    if (showDeviceDialog) {
        AlertDialog(
            onDismissRequest = { showDeviceDialog = false },
            title = { Text("Audio Output & Bridge", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Headphones, null, tint = StatusGreen, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Phone Speaker / Bluetooth", color = TextWhite, fontWeight = FontWeight.Bold)
                            Text("Active Output", color = StatusGreen, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.Check, null, tint = StatusGreen, modifier = Modifier.size(18.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDeviceDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282C37), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // 3. Song Options Bottom Sheet
    if (showOptionsSheet) {
        val optionsSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = optionsSheetState,
            containerColor = SurfaceBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(track.title, fontWeight = FontWeight.Bold, color = TextWhite, fontSize = 16.sp)
                Text(track.artist.name, color = TextGreySecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Listen to ${track.title} on Musync")
                                putExtra(Intent.EXTRA_TEXT, "Listen to ${track.title} by ${track.artist.name} on Musync: musync://track/${track.id}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Track"))
                            showOptionsSheet = false
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Share, null, tint = IconWhite, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Share Track", color = TextWhite)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onToggleFavorite()
                            showOptionsSheet = false
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = IconWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(if (isFavorite) "Remove from Favorites" else "Save to Favorites", color = TextWhite)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showOptionsSheet = false
                            onOpenQueue()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = IconWhite, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Go to Queue", color = TextWhite)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showOptionsSheet = false
                            showEqualizerSheet = true
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Speaker, null, tint = IconWhite, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Sound Profile & EQ", color = TextWhite)
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

