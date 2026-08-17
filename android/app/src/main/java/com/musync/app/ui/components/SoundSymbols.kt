package com.musync.app.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Authentic brand symbols and mode icons rendered in crisp dark glass styling.
 */
@Composable
fun SoundEngineSymbol(
    engineId: String,
    tint: Color = Color.White,
    modifier: Modifier = Modifier.size(24.dp)
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (engineId) {
            "dolby" -> {
                // Iconic Dolby double-D silhouette
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    val barW = w * 0.18f
                    val dW = w * 0.38f

                    // Left inverted D (back-to-back)
                    val leftPath = Path().apply {
                        moveTo(w * 0.44f, 0f)
                        lineTo(w * 0.44f - dW + barW, 0f)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(0f, 0f, (w * 0.44f - dW + barW) * 2, h),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = -180f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.44f, h)
                        lineTo(w * 0.44f, h * 0.72f)
                        lineTo(w * 0.28f, h * 0.72f)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(w * 0.1f, h * 0.28f, w * 0.46f, h * 0.72f),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.44f, h * 0.28f)
                        close()
                    }
                    // Right D
                    val rightPath = Path().apply {
                        moveTo(w * 0.56f, 0f)
                        lineTo(w * 0.56f + dW - barW, 0f)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(w * 0.56f - (dW - barW), 0f, w, h),
                            startAngleDegrees = 270f,
                            sweepAngleDegrees = 180f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.56f, h)
                        lineTo(w * 0.56f, h * 0.72f)
                        lineTo(w * 0.72f, h * 0.72f)
                        arcTo(
                            rect = androidx.compose.ui.geometry.Rect(w * 0.54f, h * 0.28f, w * 0.9f, h * 0.72f),
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = -180f,
                            forceMoveTo = false
                        )
                        lineTo(w * 0.56f, h * 0.28f)
                        close()
                    }

                    drawRect(color = tint, topLeft = Offset(w * 0.05f, 0f), size = Size(w * 0.38f, h))
                    drawRect(color = tint, topLeft = Offset(w * 0.57f, 0f), size = Size(w * 0.38f, h))
                    drawCircle(color = Color.Black, radius = h * 0.32f, center = Offset(w * 0.43f, h * 0.5f))
                    drawCircle(color = Color.Black, radius = h * 0.32f, center = Offset(w * 0.57f, h * 0.5f))
                }
            }
            "sony" -> {
                // Sony 360 Reality Audio target sphere symbol
                Canvas(modifier = Modifier.size(22.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(color = tint, radius = r * 0.95f, center = c, style = Stroke(width = 2.dp.toPx()))
                    drawCircle(color = tint, radius = r * 0.60f, center = c, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(color = tint, radius = r * 0.24f, center = c, style = Fill)
                }
            }
            "dts" -> {
                // DTS:X Angular Dynamic Emblem
                Text(
                    text = "DTS:X",
                    color = tint,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp
                )
            }
            "bose" -> {
                // Bose Active Wave Symbol
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    val path = Path().apply {
                        moveTo(0f, h * 0.5f)
                        cubicTo(w * 0.25f, h * 0.1f, w * 0.25f, h * 0.9f, w * 0.5f, h * 0.5f)
                        cubicTo(w * 0.75f, h * 0.1f, w * 0.75f, h * 0.9f, w, h * 0.5f)
                    }
                    drawPath(path = path, color = tint, style = Stroke(width = 2.5.dp.toPx()))
                }
            }
            "ambeo" -> {
                // Sennheiser AMBEO Holographic 3D coordinate cube
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = 1.5.dp.toPx())
                    // Top isometric face
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.5f, h * 0.1f)
                            lineTo(w * 0.85f, h * 0.32f)
                            lineTo(w * 0.5f, h * 0.54f)
                            lineTo(w * 0.15f, h * 0.32f)
                            close()
                        },
                        color = tint,
                        style = stroke
                    )
                    // Left face & Right face lines
                    drawLine(color = tint, start = Offset(w * 0.5f, h * 0.54f), end = Offset(w * 0.5f, h * 0.95f), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = tint, start = Offset(w * 0.15f, h * 0.32f), end = Offset(w * 0.15f, h * 0.73f), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = tint, start = Offset(w * 0.85f, h * 0.32f), end = Offset(w * 0.85f, h * 0.73f), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = tint, start = Offset(w * 0.15f, h * 0.73f), end = Offset(w * 0.5f, h * 0.95f), strokeWidth = 1.5.dp.toPx())
                    drawLine(color = tint, start = Offset(w * 0.85f, h * 0.73f), end = Offset(w * 0.5f, h * 0.95f), strokeWidth = 1.5.dp.toPx())
                }
            }
            "viper" -> {
                // Viper lightning tube / master FX bolt
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = "Viper",
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            "hires" -> {
                // Hi-Res Studio Master Direct
                Text(
                    text = "Hi-Res",
                    color = tint,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-0.5).sp
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Dedicated symbol for each sub-mode inside the sound engines.
 */
@Composable
fun SoundModeSymbol(
    iconType: String,
    modeId: String,
    tint: Color = Color.White,
    modifier: Modifier = Modifier.size(22.dp)
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            modeId.contains("dolby_music") -> {
                // Dolby Music: Note with spatial sound arcs
                Canvas(modifier = Modifier.size(22.dp)) {
                    val w = size.width
                    val h = size.height
                    drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(w * 0.35f, h * 0.75f))
                    drawLine(color = tint, start = Offset(w * 0.43f, h * 0.75f), end = Offset(w * 0.43f, h * 0.25f), strokeWidth = 2.dp.toPx())
                    drawLine(color = tint, start = Offset(w * 0.43f, h * 0.25f), end = Offset(w * 0.75f, h * 0.15f), strokeWidth = 2.dp.toPx())
                    // Sound waves
                    drawArc(
                        color = tint,
                        startAngle = -45f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(w * 0.5f, h * 0.3f),
                        size = Size(w * 0.4f, h * 0.4f),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            modeId.contains("dolby_cinema") || modeId.contains("dts_cinema") -> {
                // Cinema: Film clapper / projector screen
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = "Cinema",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("dolby_studio") || modeId.contains("studio") -> {
                // Studio: Studio Microphone
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Studio",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("bass") || modeId.contains("exciter") || modeId.contains("impact") -> {
                // Subwoofer Slam / Pulse
                Icon(
                    imageVector = Icons.Default.Speaker,
                    contentDescription = "Bass",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("immersion") || modeId.contains("360") -> {
                // Sony 360 Sphere Orbit
                Canvas(modifier = Modifier.size(22.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = size.minDimension / 2f
                    drawCircle(color = tint, radius = r * 0.9f, center = c, style = Stroke(width = 1.8.dp.toPx()))
                    drawCircle(color = tint, radius = r * 0.35f, center = c, style = Fill)
                    drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(c.x + r * 0.65f, c.y - r * 0.3f), style = Fill)
                }
            }
            modeId.contains("arena") || modeId.contains("concert") -> {
                // Concert Arena
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Arena",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("vocal") -> {
                // Vocal Isolation
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Vocal",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("warm") || modeId.contains("tube") -> {
                // Analog Tube / Warmth Curve
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Warm",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("hires") || modeId.contains("direct") -> {
                // Direct Bit-Perfect Headphones
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = "Direct",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            modeId.contains("air") -> {
                // Treble Air
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Air",
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
