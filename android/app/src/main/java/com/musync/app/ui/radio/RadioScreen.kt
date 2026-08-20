package com.musync.app.ui.radio

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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.MusyncApplication
import com.musync.app.playback.SoundEngine
import com.musync.app.ui.components.SectionHeader
import com.musync.app.ui.theme.AppleMusicRed
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite

@Composable
fun RadioScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MusyncApplication
    val audioEffectManager = app.container.audioEffectManager
    val eqState by audioEffectManager.state.collectAsState()

    val radioStations = listOf(
        RadioStation("Apple Hits 1", "Global Top Chart Radio", listOf(Color(0xFFFA2D48), Color(0xFFFF7E40))),
        RadioStation("Spatial Vibes", "Dolby Atmos 3D Live Station", listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))),
        RadioStation("Chill Lounge", "Lo-Fi Beats & Ambient", listOf(Color(0xFF2C3E50), Color(0xFF3498DB))),
        RadioStation("Club Dance", "High Energy EDM & Bass", listOf(Color(0xFF11998E), Color(0xFF38EF7D)))
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 160.dp)
    ) {
        // 1. Header: "Radio"
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Radio",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = TextWhite
                )
                Text(
                    text = "Spatial Sound Engines & Live Broadcasts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextGreySecondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 2. Featured Live Radio Carousel
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(radioStations) { station ->
                    Box(
                        modifier = Modifier
                            .width(260.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Brush.linearGradient(station.gradient))
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0x44000000))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "LIVE RADIO",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = station.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = station.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xDDFFFFFF)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 3. Audio Effect Engine Switcher
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161618))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (eqState.isEnabled) AppleMusicRed else Color(0xFF2A2A2E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Spatial Sound Engine",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextWhite
                            )
                            Text(
                                text = if (eqState.isEnabled) "Active: ${eqState.currentEngine.title}" else "Studio Direct (Bypass)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (eqState.isEnabled) AppleMusicRed else TextGreyMuted
                            )
                        }
                    }

                    Switch(
                        checked = eqState.isEnabled,
                        onCheckedChange = { audioEffectManager.setEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppleMusicRed,
                            uncheckedThumbColor = Color(0xFF888888),
                            uncheckedTrackColor = Color(0xFF2A2A2E)
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // 4. Sound Engine Profiles
        item {
            SectionHeader(title = "Hardware & Spatial Sound Profiles")
        }

        items(SoundEngine.entries) { engine ->
            val isSelected = eqState.currentEngine == engine && eqState.isEnabled
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) Color(0x22FA2D48) else Color(0xFF141416))
                    .border(1.dp, if (isSelected) AppleMusicRed else Color(0x18FFFFFF), RoundedCornerShape(14.dp))
                    .clickable {
                        audioEffectManager.setSoundEngine(engine)
                    }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = engine.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp
                            ),
                            color = if (isSelected) Color.White else TextWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = engine.recommendation,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = if (isSelected) AppleMusicRed else TextGreySecondary
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppleMusicRed)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class RadioStation(
    val title: String,
    val subtitle: String,
    val gradient: List<Color>
)
