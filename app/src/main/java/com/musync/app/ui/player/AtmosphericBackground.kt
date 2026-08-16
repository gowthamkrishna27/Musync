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
import com.musync.app.util.ImageQualityHelper

/**
 * Edge-to-Edge Glassy Atmospheric Ambient Background.
 *
 * Extends the vibrant, glowing blurred artwork across the ENTIRE player screen (including the bottom controls).
 * - Full-bleed frosted glass blur (60.dp) + gentle breathing glow.
 * - Balanced translucent glassmorphic gradient ensures text/control legibility without blacking out the bottom.
 * - 0kb extra network bandwidth (uses cached HD album art bitmap).
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
        initialValue = 1.08f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientScale"
    )
    val ambientAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
    ) {
        // 1. AMBIENT BLURRED ARTWORK LAYER (Full-bleed across top, center & bottom)
        if (!artworkUrl.isNullOrBlank() || trackId.isNotBlank()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(if (isPlaying) ambientScale else 1.10f)
                    .blur(60.dp)
                    .alpha(if (isPlaying) ambientAlpha else 0.50f),
                contentScale = ContentScale.Crop
            )
        }

        // 2. TRANSLUCENT GLASS OVERLAY (Allows full bottom artwork glow through controls)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x8A080A0E), // 54% dark at top for status bar legibility
                            Color(0x35080A0E), // 20% dark in center for vibrant visual glow
                            Color(0x45080A0E), // 27% dark behind seekbar
                            Color(0x55080A0E)  // 33% glassy dark at bottom — full artwork glow shines through
                        )
                    )
                )
        )

        // 3. SUBTLE FROSTED GLASS SHEEN
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0x18FFFFFF),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
