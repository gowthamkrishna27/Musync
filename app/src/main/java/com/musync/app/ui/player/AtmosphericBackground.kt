package com.musync.app.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.util.ImageQualityHelper

/**
 * Ultra-Lightweight Atmospheric Ambient Background.
 *
 * Renders behind the existing music player UI with 0kb extra network bandwidth.
 * - Utilizes the cached HD album art bitmap.
 * - Ambient frosted glass blur (50.dp) + gentle breathing glow when playing.
 * - Multi-stop dark vertical gradient protects 100% typography contrast and controls.
 * - Non-interactive and completely decoupled from audio playback pipeline.
 */
@Composable
fun AtmosphericBackground(
    artworkUrl: String?,
    trackId: String,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = ImageQualityHelper.buildOptimizedImageRequest(context, artworkUrl, trackId)

    val infiniteTransition = rememberInfiniteTransition(label = "ambientAtmosphereTransition")
    val ambientScale by infiniteTransition.animateFloat(
        initialValue = 1.05f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )
    val ambientAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
    ) {
        // 1. AMBIENT BLURRED ARTWORK LAYER (0kb extra bandwidth, instant render)
        if (!artworkUrl.isNullOrBlank() || trackId.isNotBlank()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (isPlaying) ambientScale else 1.05f)
                    .blur(50.dp)
                    .alpha(if (isPlaying) ambientAlpha else 0.30f),
                contentScale = ContentScale.Crop
            )
        }

        // 2. DARK / GRADIENT PROTECTIVE OVERLAY
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xD90B0D13), // 85% dark at top
                            Color(0x730B0D13), // 45% translucent in center for ambient glow
                            Color(0xFA0B0D13)  // 98% solid dark at bottom behind player controls
                        )
                    )
                )
        )
    }
}
