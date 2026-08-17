package com.musync.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Minimal, clean UI symbols for each sound engine.
 */
@Composable
fun SoundEngineSymbol(
    engineId: String,
    tint: Color = Color.White,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val icon = when (engineId) {
            "dolby" -> Icons.Default.GraphicEq
            "sony" -> Icons.Default.GraphicEq
            "dts" -> Icons.Default.Speaker
            "bose" -> Icons.Default.Tune
            "ambeo" -> Icons.Default.VolumeUp
            "viper" -> Icons.Default.Bolt
            "hires" -> Icons.Default.Headphones
            else -> Icons.Default.GraphicEq
        }
        Icon(
            imageVector = icon,
            contentDescription = engineId,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Minimal, clean UI symbols for each mode.
 */
@Composable
fun SoundModeSymbol(
    iconType: String,
    modeId: String,
    tint: Color = Color.White,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val icon = when {
            modeId.contains("music") || modeId.contains("arena") || modeId.contains("concert") -> Icons.Default.MusicNote
            modeId.contains("cinema") || modeId.contains("movie") -> Icons.Default.Movie
            modeId.contains("studio") || modeId.contains("vocal") -> Icons.Default.Mic
            modeId.contains("bass") || modeId.contains("exciter") || modeId.contains("impact") -> Icons.Default.Speaker
            modeId.contains("warm") || modeId.contains("tube") -> Icons.Default.Tune
            modeId.contains("hires") || modeId.contains("direct") -> Icons.Default.Headphones
            modeId.contains("bolt") -> Icons.Default.Bolt
            else -> when (iconType) {
                "music" -> Icons.Default.MusicNote
                "cinema" -> Icons.Default.Movie
                "studio", "vocal" -> Icons.Default.Mic
                "bolt" -> Icons.Default.Bolt
                "warm" -> Icons.Default.Tune
                "direct" -> Icons.Default.Headphones
                else -> Icons.Default.GraphicEq
            }
        }
        Icon(
            imageVector = icon,
            contentDescription = modeId,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
