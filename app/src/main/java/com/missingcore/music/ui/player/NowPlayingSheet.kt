package com.missingcore.music.ui.player

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material.icons.outlined.FavoriteBorder
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.missingcore.music.domain.model.PlaybackState
import com.missingcore.music.domain.model.RepeatMode
import com.missingcore.music.ui.theme.BackgroundBlack
import com.missingcore.music.ui.theme.BorderStroke
import com.missingcore.music.ui.theme.CardElevated
import com.missingcore.music.ui.theme.IconGrey
import com.missingcore.music.ui.theme.IconMuted
import com.missingcore.music.ui.theme.IconWhite
import com.missingcore.music.ui.theme.StatusGreen
import com.missingcore.music.ui.theme.SurfaceBlack
import com.missingcore.music.ui.theme.TextGreyMuted
import com.missingcore.music.ui.theme.TextGreySecondary
import com.missingcore.music.ui.theme.TextWhite

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
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderDragPosition by remember { mutableFloatStateOf(0f) }

    var showEqualizerSheet by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }

    var selectedEqPreset by remember { mutableStateOf("Bass Boost") }
    var bassLevel by remember { mutableFloatStateOf(0.7f) }
    var midLevel by remember { mutableFloatStateOf(0.5f) }
    var trebleLevel by remember { mutableFloatStateOf(0.6f) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BackgroundBlack,
        dragHandle = null
    ) {
        val playingFromText = remember(track) {
            when {
                track.id.startsWith("local") -> "Device Audio"
                !track.genre.isNullOrBlank() && track.genre != "Music" -> track.genre
                else -> "${track.artist.name} Radio"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundBlack)
                .padding(horizontal = 24.dp)
                .padding(top = 48.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Header: Down Arrow, "PLAYING FROM", 3-dots
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
                    if (!track.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        com.missingcore.music.ui.components.DefaultArtworkView(
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

            // 6. Bottom Tool Row (Queue, Equalizer/Tune, Devices)
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

    // 1. Equalizer Modal Bottom Sheet
    if (showEqualizerSheet) {
        val eqSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            sheetState = eqSheetState,
            containerColor = SurfaceBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, null, tint = StatusGreen, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Built-in Sound Equalizer",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextWhite
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // EQ Presets
                val presets = listOf("Flat", "Bass Boost", "Vocal Focus", "Treble Boost", "Rock")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { preset ->
                        val isSelected = selectedEqPreset == preset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) StatusGreen else CardElevated)
                                .border(1.dp, if (isSelected) StatusGreen else BorderStroke, RoundedCornerShape(20.dp))
                                .clickable {
                                    selectedEqPreset = preset
                                    when (preset) {
                                        "Flat" -> { bassLevel = 0.5f; midLevel = 0.5f; trebleLevel = 0.5f }
                                        "Bass Boost" -> { bassLevel = 0.85f; midLevel = 0.5f; trebleLevel = 0.5f }
                                        "Vocal Focus" -> { bassLevel = 0.4f; midLevel = 0.8f; trebleLevel = 0.5f }
                                        "Treble Boost" -> { bassLevel = 0.4f; midLevel = 0.5f; trebleLevel = 0.85f }
                                        "Rock" -> { bassLevel = 0.75f; midLevel = 0.4f; trebleLevel = 0.75f }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = preset,
                                color = if (isSelected) Color.Black else TextWhite,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bass Slider
                Text("Bass (${(bassLevel * 100).toInt()}%)", color = TextGreySecondary, fontSize = 12.sp)
                Slider(
                    value = bassLevel,
                    onValueChange = { bassLevel = it; selectedEqPreset = "Custom" },
                    colors = SliderDefaults.colors(thumbColor = StatusGreen, activeTrackColor = StatusGreen)
                )

                // Mid Slider
                Text("Mids (${(midLevel * 100).toInt()}%)", color = TextGreySecondary, fontSize = 12.sp)
                Slider(
                    value = midLevel,
                    onValueChange = { midLevel = it; selectedEqPreset = "Custom" },
                    colors = SliderDefaults.colors(thumbColor = StatusGreen, activeTrackColor = StatusGreen)
                )

                // Treble Slider
                Text("Treble (${(trebleLevel * 100).toInt()}%)", color = TextGreySecondary, fontSize = 12.sp)
                Slider(
                    value = trebleLevel,
                    onValueChange = { trebleLevel = it; selectedEqPreset = "Custom" },
                    colors = SliderDefaults.colors(thumbColor = StatusGreen, activeTrackColor = StatusGreen)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { showEqualizerSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF282C37), contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Text("Apply Profile", fontWeight = FontWeight.Bold)
                }
            }
        }
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
