package com.missingcore.music.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.missingcore.music.ui.theme.BackgroundBlack
import com.missingcore.music.ui.theme.BorderStroke
import com.missingcore.music.ui.theme.CardElevated
import com.missingcore.music.ui.theme.IconGrey
import com.missingcore.music.ui.theme.StatusGreen
import com.missingcore.music.ui.theme.SurfaceBlack
import com.missingcore.music.ui.theme.TextGreyMuted
import com.missingcore.music.ui.theme.TextGreySecondary
import com.missingcore.music.ui.theme.TextWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.missingcore.music.MusyncApplication
    val audioEffectManager = app.container.audioEffectManager
    val eqState by audioEffectManager.state.collectAsState()
    val appUpdateManager = app.container.appUpdateManager
    val updateState by appUpdateManager.updateState.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    var showCustomApiDialog by remember { mutableStateOf(false) }
    var autoplayEnabled by remember { mutableStateOf(true) }
    var showCrossfadeDialog by remember { mutableStateOf(false) }
    var selectedCrossfade by remember { mutableStateOf("5 sec") }
    var showEqDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHapticsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .padding(top = 14.dp)
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
            // Group 1: Online Music API
            item {
                SettingsSectionTitle("Online Music Source")
                SettingsCardContainer {
                    SettingsRow(
                        title = "Custom Music API",
                        value = if (uiState.isCustomApiConfigured) "Configured ✓" else "Add API Endpoint",
                        valueColor = if (uiState.isCustomApiConfigured) StatusGreen else TextGreyMuted,
                        onClick = { showCustomApiDialog = true }
                    )
                }
            }

            // Group 2: Playback & Audio
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsSectionTitle("Playback & Audio")
                SettingsCardContainer {
                    SettingsRow(
                        title = "Streaming Quality",
                        value = if (uiState.audioQuality == "high") "High (320kbps)" else "Standard (128kbps)",
                        onClick = {
                            viewModel.onAudioQualityChange(if (uiState.audioQuality == "high") "standard" else "high")
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        title = "Equalizer",
                        value = if (eqState.isEnabled) eqState.activePreset else "Disabled",
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
                        valueColor = if (uiState.hapticIntensity == com.missingcore.music.playback.HapticIntensity.OFF) TextGreyMuted else StatusGreen,
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

            // Group 3: About
            item {
                Spacer(modifier = Modifier.height(20.dp))
                SettingsSectionTitle("About")
                SettingsCardContainer {
                    SettingsRow(
                        title = "Check for Updates",
                        value = "v${com.missingcore.music.BuildConfig.VERSION_NAME}",
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
                }
            }
        }
    }

    // Add Custom API Dialog (No hardcoded default providers)
    if (showCustomApiDialog) {
        AlertDialog(
            onDismissRequest = { showCustomApiDialog = false },
            title = { Text("Add Custom Music API", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter your custom music server or streaming endpoint URL. Leave empty to run in local offline device mode.",
                        color = TextGreySecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text("API Base URL / Endpoint", color = TextGreySecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.customApiUrl,
                        onValueChange = { viewModel.onCustomApiUrlChange(it) },
                        placeholder = { Text("https://your-api-endpoint.com/v1/", color = TextGreyMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardElevated,
                            unfocusedContainerColor = CardElevated,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = BorderStroke,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("API Key (Optional)", color = TextGreySecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.apiKey,
                        onValueChange = { viewModel.onApiKeyChange(it) },
                        placeholder = { Text("Optional API token / key", color = TextGreyMuted, fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = CardElevated,
                            unfocusedContainerColor = CardElevated,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = BorderStroke,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (uiState.statusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.statusMessage ?: "",
                            color = if (uiState.connectionStatus == ConnectionStatus.SUCCESS) StatusGreen else Color(0xFFEF4444),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (uiState.isCustomApiConfigured || uiState.customApiUrl.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22EF4444))
                                .border(1.dp, Color(0x55EF4444), RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.clearCustomApi()
                                    showCustomApiDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Remove", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x35FFFFFF))
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(8.dp))
                            .clickable {
                                viewModel.saveCustomApi()
                                if (uiState.customApiUrl.isBlank()) {
                                    showCustomApiDialog = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.connectionStatus == ConnectionStatus.TESTING) "Connecting..." else "Save & Connect",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { showCustomApiDialog = false }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Cancel", color = Color(0xFFCCCCCC), fontSize = 12.sp)
                }
            },
            containerColor = SurfaceBlack,
            shape = RoundedCornerShape(14.dp)
        )
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

    // Equalizer Profile Dialog
    if (showEqDialog) {
        AlertDialog(
            onDismissRequest = { showEqDialog = false },
            title = { Text("Equalizer Preset", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    val eqOptions = eqState.availablePresets
                    eqOptions.forEach { eq ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    audioEffectManager.setPreset(eq)
                                    showEqDialog = false
                                }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(eq, color = TextWhite, fontSize = 14.sp)
                            if (eqState.activePreset == eq) {
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
                        .clickable { showEqDialog = false }
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        )
                        .clickable { showTermsDialog = false }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("I Understand", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
                            )
                        )
                        .clickable { showPrivacyDialog = false }
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Got It", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                        com.missingcore.music.playback.HapticIntensity.OFF to "No vibration during playback",
                        com.missingcore.music.playback.HapticIntensity.SUBTLE to "Light rhythmic micro-ticks",
                        com.missingcore.music.playback.HapticIntensity.BALANCED to "Pleasant bass transient pulses",
                        com.missingcore.music.playback.HapticIntensity.HEAVY to "Strong kick & bass impact"
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
                if (updateState !is com.missingcore.music.update.UpdateDownloadState.Downloading) {
                    showUpdateDialog = false
                    appUpdateManager.resetState()
                }
            },
            title = {
                Text(
                    text = when (updateState) {
                        is com.missingcore.music.update.UpdateDownloadState.Checking -> "Checking for Updates"
                        is com.missingcore.music.update.UpdateDownloadState.Available -> "Update Available! 🚀"
                        is com.missingcore.music.update.UpdateDownloadState.Downloading -> "Downloading Update..."
                        is com.missingcore.music.update.UpdateDownloadState.ReadyToInstall -> "Ready to Install"
                        is com.missingcore.music.update.UpdateDownloadState.UpToDate -> "You're Up to Date!"
                        is com.missingcore.music.update.UpdateDownloadState.Error -> "Update Check Failed"
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
                        is com.missingcore.music.update.UpdateDownloadState.Checking -> {
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
                        is com.missingcore.music.update.UpdateDownloadState.UpToDate -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "Musync v${com.missingcore.music.BuildConfig.VERSION_NAME} is currently the latest version.",
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
                        is com.missingcore.music.update.UpdateDownloadState.Available -> {
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
                        is com.missingcore.music.update.UpdateDownloadState.Downloading -> {
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
                        is com.missingcore.music.update.UpdateDownloadState.ReadyToInstall -> {
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
                        is com.missingcore.music.update.UpdateDownloadState.Error -> {
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
                    is com.missingcore.music.update.UpdateDownloadState.Available -> {
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
                    is com.missingcore.music.update.UpdateDownloadState.ReadyToInstall -> {
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
                    is com.missingcore.music.update.UpdateDownloadState.Error -> {
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
                if (updateState !is com.missingcore.music.update.UpdateDownloadState.Downloading) {
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
