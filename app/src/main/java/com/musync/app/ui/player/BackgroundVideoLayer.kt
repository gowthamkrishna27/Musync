package com.musync.app.ui.player

import android.content.Context
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * High-Performance Decoupled Background Video Atmosphere Layer.
 *
 * Key Architectural Invariants:
 * 1. Audio playback is 100% independent and NEVER waits for video buffering or decoders.
 * 2. Video loads asynchronously in background; fades in (opacity 0 -> 1) once the first frame renders.
 * 3. Video failure or slow network drops the video layer silently while primary audio continues.
 * 4. Surface lifecycle is tied to UI visibility: halts decoding on background/screen-off to preserve battery.
 * 5. Touch-transparent: zero pointer interception (100% of gestures reach foreground player UI).
 */
@OptIn(UnstableApi::class)
@Composable
fun BackgroundVideoAtmosphere(
    videoUrl: String?,
    isVideoEnabled: Boolean,
    isAudioPlaying: Boolean,
    audioPositionMs: Long,
    modifier: Modifier = Modifier
) {
    if (!isVideoEnabled || videoUrl.isNullOrBlank()) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isFirstFrameReady by remember { mutableStateOf(false) }

    // Smooth opacity transition: 0.0f while loading -> 1.0f when first frame decoded
    val videoAlpha by animateFloatAsState(
        targetValue = if (isFirstFrameReady && isVideoEnabled) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 600),
        label = "videoAtmosphereAlpha"
    )

    var visualPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

    DisposableEffect(videoUrl) {
        if (videoUrl.isNullOrBlank()) {
            return@DisposableEffect onDispose {}
        }

        // Lightweight Visual-Only ExoPlayer (Muted audio, aggressive video buffering)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs
                30000, // maxBufferMs
                500,   // bufferForPlaybackMs (fast visual start)
                1000   // bufferForPlaybackAfterRebufferMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            setEnableDecoderFallback(true)
        }

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                volume = 0f // Completely muted to avoid interfering with primary AudioTrack
                repeatMode = Player.REPEAT_MODE_ALL
                playWhenReady = isAudioPlaying

                val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                setMediaItem(mediaItem)
                prepare()
                if (audioPositionMs > 0) {
                    seekTo(audioPositionMs)
                }
            }

        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                isFirstFrameReady = true
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.w("BackgroundVideoAtmosphere", "Visual atmosphere load failed: ${error.message}. Hiding video layer while audio continues.")
                isFirstFrameReady = false
            }
        }

        player.addListener(listener)
        visualPlayer = player

        onDispose {
            isFirstFrameReady = false
            player.removeListener(listener)
            player.release()
            visualPlayer = null
        }
    }

    // Sync play/pause state with primary audio player
    LaunchedEffect(isAudioPlaying) {
        visualPlayer?.let { player ->
            if (isAudioPlaying) {
                if (!player.isPlaying && player.playbackState == Player.STATE_READY) {
                    player.play()
                }
            } else {
                if (player.isPlaying) {
                    player.pause()
                }
            }
        }
    }

    // Sync seek position if drift exceeds 3000ms
    LaunchedEffect(audioPositionMs) {
        visualPlayer?.let { player ->
            val diff = Math.abs(player.currentPosition - audioPositionMs)
            if (diff > 3500L && player.playbackState == Player.STATE_READY) {
                player.seekTo(audioPositionMs)
            }
        }
    }

    // Lifecycle observer: pause video rendering when app is backgrounded / screen off
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    visualPlayer?.playWhenReady = false
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (isAudioPlaying) {
                        visualPlayer?.playWhenReady = true
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. VIDEO LAYER (TextureView via PlayerView, CENTER_CROP)
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = visualPlayer
                }
            },
            update = { playerView ->
                if (playerView.player != visualPlayer) {
                    playerView.player = visualPlayer
                }
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha)
        )

        // 2. ATMOSPHERIC GRADIENT TINT (Protects foreground typography and controls)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xCC0B0D13), // 80% dark top behind header
                            Color(0x550B0D13), // 33% translucent center
                            Color(0xF50B0D13)  // 96% dark bottom behind controls & progress
                        )
                    )
                )
        )
    }
}
