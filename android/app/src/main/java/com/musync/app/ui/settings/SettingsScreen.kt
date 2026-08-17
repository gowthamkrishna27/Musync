package com.musync.app.ui.settings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.BorderStroke
import com.musync.app.ui.theme.CardElevated
import com.musync.app.ui.theme.IconGrey
import com.musync.app.ui.theme.StatusGreen
import com.musync.app.ui.theme.SurfaceBlack
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite
import androidx.compose.foundation.layout.statusBarsPadding
import com.musync.app.ui.auth.AccountProfileCard
import com.musync.app.ui.auth.AuthBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.musync.app.MusyncApplication
    val audioEffectManager = app.container.audioEffectManager
    val eqState by audioEffectManager.state.collectAsState()
    val appUpdateManager = app.container.appUpdateManager
    val updateState by appUpdateManager.updateState.collectAsState()
    val currentUser by viewModel.authManager.currentUser.collectAsState()
    val syncStatus by viewModel.authManager.syncStatus.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    var autoplayEnabled by remember { mutableStateOf(true) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var selectedCrossfade by remember { mutableStateOf("5 sec") }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showEqDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHapticsDialog by remember { mutableStateOf(false) }
    var showAuthSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
            .padding(top = 8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = TextWhite,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp)
        ) {
            // Group 0: Account & Cloud Sync
            item {
                SettingsSectionTitle("Account & Cloud Backup")
                AccountProfileCard(
                    user = currentUser,
                    syncStatus = syncStatus,
                    onSignInClick = { showAuthSheet = true },
                    onSyncClick = { viewModel.cloudSyncManager.triggerSync() },
                    onSignOutClick = { viewModel.authManager.signOut() }
                )
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Group 1: Playback & Audio
            item {
                SettingsSectionTitle("Playback & Audio")
                SettingsCardContainer {
                    SettingsRow(
                        title = "Streaming Quality",
                        value = when (uiState.audioQuality) {
                            "low", "saver" -> "Data Saver (64kbps)"
                            "standard" -> "Standard (128kbps)"
                            else -> "High (320kbps)"
                        },
                        onClick = { showQualityDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Studio & Spatial Audio",
                        value = if (eqState.isEnabled) eqState.currentMode.title else "Disabled",
                        valueColor = if (eqState.isEnabled) Color.White else TextGreyMuted,
                        onClick = { showEqDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Crossfade",
                        value = selectedCrossfade,
                        onClick = { showCrossfadeDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Beat Haptics (Bass Sync)",
                        value = uiState.hapticIntensity.description,
                        valueColor = if (uiState.hapticIntensity == com.musync.app.playback.HapticIntensity.OFF) TextGreyMuted else StatusGreen,
                        onClick = { showHapticsDialog = true }
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Autoplay",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = TextWhite
                        )
                        Switch(
                            checked = autoplayEnabled,
                            onCheckedChange = { autoplayEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = TextGreySecondary,
                                uncheckedTrackColor = CardElevated
                            )
                        )
                    }
                }
            }

            // Group 2: About
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsSectionTitle("About")
                SettingsCardContainer {
                    SettingsRow(
                        title = "Check for Updates",
                        value = "v${com.musync.app.BuildConfig.VERSION_NAME}",
                        valueColor = StatusGreen,
                        onClick = {
                            showUpdateDialog = true
                            scope.launch {
                                appUpdateManager.checkForUpdates(silent = false)
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Terms of Service",
                        value = "",
                        onClick = { showTermsDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Privacy Policy",
                        value = "",
                        onClick = { showPrivacyDialog = true }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Developer",
                        value = "@gowthamchowdary.27",
                        valueColor = StatusGreen,
                        onClick = {
                            try {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://instagram.com/gowthamchowdary.27")
                                )
                                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
        }
    }

    // Crossfade Dialog
    if (showCrossfadeDialog) {
        AlertDialog(
            onDismissRequest = { showCrossfadeDialog = false },
            title = { Text("Crossfade Transition", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val options = listOf("Off", "3 sec", "5 sec", "8 sec", "12 sec")
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCrossfade = opt
                                    showCrossfadeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(opt, color = TextWhite, fontSize = 14.sp)
                            if (selectedCrossfade == opt) {
                                Text("✓", color = StatusGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showCrossfadeDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Streaming Quality Dialog
    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text("Streaming Quality", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val qualities = listOf(
                        Triple("high", "High (320kbps)", "Best studio sound clarity & dynamic range"),
                        Triple("standard", "Standard (128kbps)", "Balanced mobile data & smooth audio"),
                        Triple("saver", "Data Saver (64kbps)", "Ultra-low data usage for 2G / weak cellular")
                    )

                    qualities.forEach { (key, title, subtitle) ->
                        val isSelected = uiState.audioQuality == key || (key == "saver" && uiState.audioQuality == "low")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0x22FFFFFF) else CardElevated)
                                .border(1.dp, if (isSelected) Color.White else BorderStroke, RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.onAudioQualityChange(key)
                                    showQualityDialog = false
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(subtitle, color = TextGreySecondary, fontSize = 11.sp)
                                }
                                if (isSelected) {
                                    Text("✓", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showQualityDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Close", color = Color.White, fontSize = 12.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Dolby Atmos 3D Audio Dialog
    if (showEqDialog) {
        val scrollState = androidx.compose.foundation.rememberScrollState()
        val user = currentUser
        val isLoggedIn = user != null && !user.isAnonymous
        var isEngineDropdownExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = isLoggedIn) {
                            audioEffectManager.setEnabled(!eqState.isEnabled)
                        }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x35FFFFFF), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.musync.app.ui.components.SoundEngineSymbol(
                                engineId = eqState.currentEngine.id,
                                tint = if (isLoggedIn && eqState.isEnabled) Color.White else IconGrey,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Studio & Spatial Audio", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (!isLoggedIn) "Member Exclusive" else if (eqState.isEnabled) "DSP Engine Active" else "Bypassed",
                                color = if (!isLoggedIn) Color(0xBBFFFFFF) else if (eqState.isEnabled) Color.White else TextGreyMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    if (isLoggedIn) {
                        Switch(
                            checked = eqState.isEnabled,
                            onCheckedChange = { audioEffectManager.setEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = Color.White,
                                uncheckedThumbColor = TextGreySecondary,
                                uncheckedTrackColor = Color(0x22FFFFFF)
                            )
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                    if (!isLoggedIn) {
                        // Translucent Dark Glass Member Exclusive Lock Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color(0x28FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Member Exclusive Sound",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Sign in to unlock all 7 spatial sound engines, sub-modes, and psychoacoustic hardware DSP.",
                                    color = Color(0x99FFFFFF),
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        showEqDialog = false
                                        showAuthSheet = true
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(42.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Sign In to Unlock", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

                        Spacer(modifier = Modifier.height(8.dp))

                        val signatureEngines = com.musync.app.playback.SoundEngineRegistry.signatureEngines

                        // 7 Engine Square Buttons Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            signatureEngines.forEach { engineMode ->
                                val isEngineSelected = eqState.currentEngine == engineMode.engine

                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isEngineSelected && eqState.isEnabled) Color(0x38FFFFFF) else Color(0x14FFFFFF)
                                        )
                                        .border(
                                            if (isEngineSelected && eqState.isEnabled) 1.5.dp else 1.dp,
                                            if (isEngineSelected && eqState.isEnabled) Color.White else Color(0x24FFFFFF),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            audioEffectManager.setEngineMode(engineMode)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.musync.app.ui.components.SoundEngineSymbol(
                                        engineId = engineMode.engine.id,
                                        tint = if (isEngineSelected && eqState.isEnabled) Color.White else Color(0x77FFFFFF),
                                        modifier = Modifier.size(22.dp)
                                    )

                                    if (isEngineSelected && eqState.isEnabled) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 3.dp)
                                                .size(3.5.dp)
                                                .clip(CircleShape)
                                                .background(Color.White)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Active Engine & Signature Mode Info Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x18FFFFFF))
                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp)
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
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0x24FFFFFF)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.musync.app.ui.components.SoundEngineSymbol(
                                            engineId = eqState.currentEngine.id,
                                            tint = TextWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = eqState.currentMode.title,
                                            color = TextWhite,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = eqState.currentMode.subtitle,
                                            color = Color(0x99FFFFFF),
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                val modeTag = eqState.currentMode.recommendationTag
                                if (modeTag != null) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Color(0x22FFFFFF))
                                            .border(0.8.dp, Color(0x33FFFFFF), RoundedCornerShape(5.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                    ) {
                                        Text(
                                            text = modeTag,
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .clickable { showEqDialog = false }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Done", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xF212131A),
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms of Service", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Musync is an advanced local & networked music player integrating Android MediaSession, Bluetooth AVRCP, and real-time transient haptics.",
                    color = TextGreySecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x351E222D))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { showTermsDialog = false }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I Understand", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Privacy Policy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Musync values user privacy:\n• Zero tracking or personal telemetry.\n• Custom API tokens are encrypted with Android Keystore.\n• Playback cache and favorites remain on your device in local SQLite Room DB.",
                    color = TextGreySecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x351E222D))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                        .clickable { showPrivacyDialog = false }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Got It", color = TextWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // Beat Haptics Configuration Dialog
    if (showHapticsDialog) {
        AlertDialog(
            onDismissRequest = { showHapticsDialog = false },
            title = { Text("Song Beat Haptics", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Musync synchronizes your phone's vibration motor with real-time bass and kick drum beats of currently playing music.",
                        color = TextGreySecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val hapticOptions = listOf(
                        com.musync.app.playback.HapticIntensity.OFF to "No vibration during playback",
                        com.musync.app.playback.HapticIntensity.SUBTLE to "Light rhythmic micro-ticks",
                        com.musync.app.playback.HapticIntensity.BALANCED to "Pleasant bass transient pulses",
                        com.musync.app.playback.HapticIntensity.HEAVY to "Strong kick & bass impact"
                    )

                    hapticOptions.forEach { (intensity, desc) ->
                        val isSelected = uiState.hapticIntensity == intensity
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.onHapticIntensityChange(intensity)
                                    showHapticsDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(intensity.description, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(desc, color = TextGreyMuted, fontSize = 11.sp)
                            }
                            if (isSelected) {
                                Text("✓", color = StatusGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHapticsDialog = false }) {
                    Text("Done", color = TextGreySecondary)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    // In-App OTA Update Dialog
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = {
                if (updateState !is com.musync.app.update.UpdateDownloadState.Downloading) {
                    showUpdateDialog = false
                    appUpdateManager.resetState()
                }
            },
            title = {
                Text(
                    text = when (updateState) {
                        is com.musync.app.update.UpdateDownloadState.Checking -> "Checking for Updates"
                        is com.musync.app.update.UpdateDownloadState.Available -> "Update Available! 🚀"
                        is com.musync.app.update.UpdateDownloadState.Downloading -> "Downloading Update..."
                        is com.musync.app.update.UpdateDownloadState.ReadyToInstall -> "Ready to Install"
                        is com.musync.app.update.UpdateDownloadState.UpToDate -> "You're Up to Date!"
                        is com.musync.app.update.UpdateDownloadState.Error -> "Update Check Failed"
                        else -> "App Updates"
                    },
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (val state = updateState) {
                        is com.musync.app.update.UpdateDownloadState.Checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 12.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = StatusGreen,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text("Connecting to update server...", color = TextGreySecondary, fontSize = 13.sp)
                            }
                        }
                        is com.musync.app.update.UpdateDownloadState.UpToDate -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "Musync v${com.musync.app.BuildConfig.VERSION_NAME} is currently the latest version.",
                                    color = TextWhite,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "You have all the newest features, sound equalizer improvements, and performance patches.",
                                    color = TextGreyMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        is com.musync.app.update.UpdateDownloadState.Available -> {
                            val info = state.info
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Installed: v${info.currentVersion}", color = TextGreyMuted, fontSize = 12.sp)
                                    Text("Latest: v${info.latestVersion}", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("What's New:", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CardElevated)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = info.changelog.ifBlank { "Performance improvements and bug fixes." },
                                        color = TextGreySecondary,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 6,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        is com.musync.app.update.UpdateDownloadState.Downloading -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Downloading APK...", color = TextWhite, fontSize = 12.sp)
                                    Text("${state.progressPercent}%", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.material3.LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = StatusGreen,
                                    trackColor = CardElevated
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val mbDownloaded = "%.1f".format(state.bytesDownloaded / (1024.0 * 1024.0))
                                val mbTotal = if (state.totalBytes > 0) "%.1f".format(state.totalBytes / (1024.0 * 1024.0)) else "?"
                                Text("$mbDownloaded MB / $mbTotal MB", color = TextGreyMuted, fontSize = 10.sp)
                            }
                        }
                        is com.musync.app.update.UpdateDownloadState.ReadyToInstall -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "✓ Update downloaded successfully!",
                                    color = StatusGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Install Now' to open Android package installer and complete the update.",
                                    color = TextGreySecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        is com.musync.app.update.UpdateDownloadState.Error -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = state.message,
                                    color = Color(0xFFFF5252),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        else -> {}
                    }
                }
            },
            confirmButton = {
                when (val state = updateState) {
                    is com.musync.app.update.UpdateDownloadState.Available -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    appUpdateManager.downloadAndInstallUpdate(state.info.downloadUrl, state.info.fileName)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Update Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    is com.musync.app.update.UpdateDownloadState.ReadyToInstall -> {
                        Button(
                            onClick = {
                                appUpdateManager.launchPackageInstaller(state.apkFile)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Install Now", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    is com.musync.app.update.UpdateDownloadState.Error -> {
                        Button(
                            onClick = {
                                scope.launch {
                                    appUpdateManager.checkForUpdates(silent = false)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFFFFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Retry", color = TextWhite, fontSize = 12.sp)
                        }
                    }
                    else -> {}
                }
            },
            dismissButton = {
                if (updateState !is com.musync.app.update.UpdateDownloadState.Downloading) {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        appUpdateManager.resetState()
                    }) {
                        Text("Close", color = TextGreySecondary, fontSize = 12.sp)
                    }
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
    }

    if (showAuthSheet) {
        AuthBottomSheet(
            authManager = viewModel.authManager,
            onDismiss = { showAuthSheet = false }
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        ),
        color = TextWhite,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingsCardContainer(
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardElevated)
            .border(1.dp, BorderStroke, RoundedCornerShape(12.dp))
    ) {
        content()
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    valueColor: Color = TextGreySecondary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = TextWhite
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value.isNotEmpty()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = valueColor
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = IconGrey,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderStroke)
    )
}

