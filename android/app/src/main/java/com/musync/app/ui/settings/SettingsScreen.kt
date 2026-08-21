package com.musync.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.musync.app.BuildConfig
import com.musync.app.MusyncApplication
import com.musync.app.playback.HapticIntensity
import com.musync.app.playback.SoundEngine
import com.musync.app.playback.SoundEngineRegistry
import com.musync.app.ui.theme.AppleMusicPink
import com.musync.app.ui.theme.AppleMusicRed
import com.musync.app.ui.theme.BackgroundBlack
import com.musync.app.ui.theme.DeleteRed
import com.musync.app.ui.theme.TextGreyMuted
import com.musync.app.ui.theme.TextGreySecondary
import com.musync.app.ui.theme.TextWhite
import com.musync.app.update.UpdateDownloadState
import kotlinx.coroutines.launch

enum class SettingsSection(val title: String) {
    GENERAL("General"),
    ACCOUNT("Account"),
    PLAYBACK("Playback"),
    AUDIO("Audio"),
    MUSIC_DISCOVERY("Music & Discovery"),
    LIBRARY("Library"),
    NOTIFICATIONS("Notifications"),
    APPEARANCE("Appearance"),
    PRIVACY_SECURITY("Privacy & Security"),
    STORAGE_DATA("Storage & Data"),
    APP_UPDATES("App Updates"),
    ABOUT("About Musync")
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = context.applicationContext as MusyncApplication
    val appUpdateManager = app.container.appUpdateManager
    val updateState by appUpdateManager.updateState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.authManager.currentUser.collectAsState()
    val haptic = LocalHapticFeedback.current

    var selectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    var showAuthDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.computeCacheSize(context)
    }

    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundBlack)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = selectedSection,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut())
                } else {
                    (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { it / 2 } + fadeOut())
                }
            },
            label = "settings_transition"
        ) { currentSection ->
            if (currentSection == null) {
                // ─────────────────────────────────────────────────────────────
                // ROOT SETTINGS MENU (Peak Minimalism — Typography Driven)
                // ─────────────────────────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    // Header Back Navigation
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Back",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = AppleMusicPink,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        onNavigateBack()
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Settings Heading + User Account Info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    if (currentUser == null) {
                                        showAuthDialog = true
                                    } else {
                                        selectedSection = SettingsSection.ACCOUNT
                                    }
                                }
                                .padding(bottom = 28.dp)
                        ) {
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 34.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = currentUser?.displayName ?: "Musync Listener",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = AppleMusicPink
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentUser?.email ?: "email@example.com",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 13.sp
                                ),
                                color = TextGreySecondary
                            )
                        }
                    }

                    // Subtle Divider
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0x1FFFFFFF))
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // The 12 Core Sections (Pure text navigation, zero icons)
                    items(SettingsSection.values().size) { index ->
                        val section = SettingsSection.values()[index]
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    selectedSection = section
                                }
                                .padding(vertical = 14.dp)
                        ) {
                            Text(
                                text = section.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = (-0.2).sp
                                ),
                                color = TextWhite
                            )
                        }
                    }

                    // Version & Build Footer
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Musync",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.sp
                                ),
                                color = TextGreyMuted
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME} · Build ${BuildConfig.VERSION_CODE}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = TextGreyMuted.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            } else {
                // ─────────────────────────────────────────────────────────────
                // DETAIL SETTINGS SECTION VIEW
                // ─────────────────────────────────────────────────────────────
                SettingsDetailView(
                    section = currentSection,
                    viewModel = viewModel,
                    appUpdateManager = appUpdateManager,
                    updateState = updateState,
                    uiState = uiState,
                    onBack = { selectedSection = null },
                    onOpenAuth = { showAuthDialog = true }
                )
            }
        }

        // Minimal Email & Password Auth Dialog
        if (showAuthDialog) {
            MinimalAuthDialog(
                viewModel = viewModel,
                onDismiss = { showAuthDialog = false }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DETAIL SECTION VIEWS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsDetailView(
    section: SettingsSection,
    viewModel: SettingsViewModel,
    appUpdateManager: com.musync.app.update.AppUpdateManager,
    updateState: UpdateDownloadState,
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val currentUser by viewModel.authManager.currentUser.collectAsState()

    // Dialog state variables
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showMusicLanguagesDialog by remember { mutableStateOf(false) }
    var showSingleChoiceDialog by remember { mutableStateOf<SingleChoiceConfig?>(null) }
    var showLegalDialog by remember { mutableStateOf<LegalDialogConfig?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // Back Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                    color = AppleMusicPink,
                    modifier = Modifier
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onBack()
                        }
                        .padding(vertical = 4.dp)
                )
            }
        }

        // Large Section Title
        item {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = (-0.5).sp
                ),
                color = TextWhite,
                modifier = Modifier.padding(bottom = 28.dp)
            )
        }

        // Section Content Router
        when (section) {
            SettingsSection.GENERAL -> {
                item {
                    SettingsGroupHeader(title = "Profile & Region")
                    SettingsActionRow(
                        title = "Name",
                        value = currentUser?.displayName ?: "Musync Listener",
                        onClick = { showEditNameDialog = true }
                    )
                    SettingsRow(title = "Email", value = currentUser?.email ?: "email@example.com")
                    SettingsActionRow(
                        title = "Language",
                        value = uiState.appLanguage,
                        onClick = {
                            showSingleChoiceDialog = SingleChoiceConfig(
                                title = "Select App Language",
                                options = listOf("English", "Telugu", "Hindi", "Tamil", "Kannada", "Malayalam"),
                                selected = uiState.appLanguage,
                                onSelect = { viewModel.onAppLanguageChange(it) }
                            )
                        }
                    )
                    SettingsActionRow(
                        title = "Region",
                        value = uiState.region,
                        onClick = {
                            showSingleChoiceDialog = SingleChoiceConfig(
                                title = "Select Region",
                                options = listOf("India", "Global", "United States", "United Kingdom"),
                                selected = uiState.region,
                                onSelect = { viewModel.onRegionChange(it) }
                            )
                        }
                    )
                    SettingsActionRow(
                        title = "Preferred Music Languages",
                        value = uiState.preferredMusicLanguages.joinToString(", "),
                        onClick = { showMusicLanguagesDialog = true }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroupHeader(title = "Content & Startup")
                    SettingsToggleRow(
                        title = "Allow Explicit Content",
                        subtitle = "Include explicit tracks in discovery and search results",
                        checked = uiState.allowExplicitContent,
                        onCheckedChange = { viewModel.onExplicitContentToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Default Landing Page",
                        options = listOf("Home", "New", "Offline", "Library"),
                        selected = uiState.defaultLandingPage,
                        onSelect = { viewModel.onDefaultLandingPageChange(it) }
                    )
                    SettingsOptionSelector(
                        title = "Default Playback Behavior",
                        options = listOf("Resume", "Start from beginning", "Ask"),
                        selected = uiState.defaultPlaybackBehavior,
                        onSelect = { viewModel.onDefaultPlaybackBehaviorChange(it) }
                    )
                }
            }

            SettingsSection.ACCOUNT -> {
                item {
                    SettingsGroupHeader(title = "Personal Information")
                    SettingsActionRow(
                        title = "Name",
                        value = currentUser?.displayName ?: "Musync Listener",
                        onClick = { showEditNameDialog = true }
                    )
                    SettingsRow(title = "Email", value = currentUser?.email ?: "email@example.com")
                    SettingsActionRow(
                        title = "Change Password",
                        onClick = {
                            if (currentUser != null) {
                                showChangePasswordDialog = true
                            } else {
                                onOpenAuth()
                            }
                        }
                    )
                    SettingsActionRow(
                        title = "Reset Password via Email",
                        onClick = {
                            currentUser?.email?.let { email ->
                                scope.launch {
                                    val res = viewModel.authManager.sendPasswordReset(email)
                                    if (res.isSuccess) {
                                        viewModel.showToast("Password reset email sent to $email")
                                    }
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroupHeader(title = "Active Sessions")
                    val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
                    SettingsRow(
                        title = deviceName,
                        value = "Android ${Build.VERSION.RELEASE} · Active Now"
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsGroupHeader(title = "Account Actions")
                    if (currentUser != null) {
                        SettingsActionRow(
                            title = "Sign Out",
                            textColor = AppleMusicRed,
                            onClick = { viewModel.authManager.signOut() }
                        )
                        SettingsActionRow(
                            title = "Delete Account",
                            textColor = DeleteRed,
                            onClick = { showDeleteAccountDialog = true }
                        )
                    } else {
                        SettingsActionRow(
                            title = "Sign In",
                            textColor = AppleMusicPink,
                            onClick = onOpenAuth
                        )
                    }
                }
            }

            SettingsSection.PLAYBACK -> {
                item {
                    SettingsGroupHeader(title = "Playback Controls")
                    SettingsToggleRow(
                        title = "Autoplay",
                        subtitle = "Automatically fetch and play recommended songs when queue ends",
                        checked = uiState.autoplay,
                        onCheckedChange = { viewModel.onAutoplayToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Intelligent Shuffle",
                        subtitle = "Session-aware probabilistic queue ordering based on affinities",
                        checked = uiState.intelligentShuffle,
                        onCheckedChange = { viewModel.onIntelligentShuffleToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Eliminate pauses between consecutive audio tracks",
                        checked = uiState.gaplessPlayback,
                        onCheckedChange = { viewModel.onGaplessPlaybackToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Crossfade",
                        options = listOf("Off", "2s", "4s", "6s", "8s", "10s"),
                        selected = if (uiState.crossfadeSeconds > 0) "${uiState.crossfadeSeconds}s" else "Off",
                        onSelect = { opt ->
                            val secs = opt.replace("s", "").toIntOrNull() ?: 0
                            viewModel.onCrossfadeSecondsChange(secs)
                        }
                    )
                    SettingsToggleRow(
                        title = "Continue Playing",
                        subtitle = "Remember last played track position across sessions",
                        checked = uiState.continuePlaying,
                        onCheckedChange = { viewModel.onContinuePlayingToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Queue Behavior",
                        options = listOf("Play Next", "Add to Queue", "Replace Queue"),
                        selected = uiState.queueBehavior,
                        onSelect = { viewModel.onQueueBehaviorChange(it) }
                    )
                    SettingsToggleRow(
                        title = "Record Playback History",
                        subtitle = "Save played songs to Recent History and feed personalization",
                        checked = uiState.recordListeningHistory,
                        onCheckedChange = { viewModel.onRecordHistoryToggle(it) }
                    )
                }
            }

            SettingsSection.AUDIO -> {
                item {
                    SettingsGroupHeader(title = "Audio Quality")
                    SettingsOptionSelector(
                        title = "Streaming Quality",
                        options = listOf("low", "medium", "high", "lossless"),
                        selected = uiState.audioQuality,
                        onSelect = { viewModel.onAudioQualityChange(it) }
                    )
                    SettingsOptionSelector(
                        title = "Download Quality",
                        options = listOf("standard", "high", "lossless"),
                        selected = uiState.downloadQuality,
                        onSelect = { viewModel.onDownloadQualityChange(it) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroupHeader(title = "Hardware Sound Engine & Equalizer")
                    SettingsOptionSelector(
                        title = "Equalizer Preset",
                        options = listOf("Off", "Bass Boost", "Treble Boost", "Vocal Boost", "Acoustic", "Rock", "Electronic"),
                        selected = uiState.equalizerPreset,
                        onSelect = { viewModel.onEqualizerPresetChange(it) }
                    )
                    SettingsActionRow(
                        title = "Dolby Atmos / Spatial Engine",
                        value = uiState.soundEngineTitle,
                        onClick = {
                            showSingleChoiceDialog = SingleChoiceConfig(
                                title = "Select Spatial Audio Engine",
                                options = SoundEngineRegistry.signatureEngines.map { it.title },
                                selected = uiState.soundEngineTitle,
                                onSelect = { title ->
                                    val mode = SoundEngineRegistry.signatureEngines.find { it.title == title }
                                    if (mode != null) viewModel.onSoundEngineChange(mode.engine)
                                }
                            )
                        }
                    )
                    SettingsToggleRow(
                        title = "Audio Normalization",
                        subtitle = "Maintain consistent volume levels across songs with LoudnessEnhancer",
                        checked = uiState.audioNormalization,
                        onCheckedChange = { viewModel.onAudioNormalizationToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Haptic Intensity",
                        options = listOf("OFF", "LOW", "MEDIUM", "HIGH"),
                        selected = uiState.hapticIntensity.name,
                        onSelect = {
                            val intensity = try { HapticIntensity.valueOf(it) } catch (_: Exception) { HapticIntensity.OFF }
                            viewModel.onHapticIntensityChange(intensity)
                        }
                    )
                }
            }

            SettingsSection.MUSIC_DISCOVERY -> {
                item {
                    SettingsGroupHeader(title = "Recommendation Engine")
                    SettingsToggleRow(
                        title = "Personalized Recommendations",
                        subtitle = "Use your listening session signals for suggestions",
                        checked = uiState.personalizedRecommendations,
                        onCheckedChange = { viewModel.onPersonalizedRecommendationsToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Recommendation Personalization Level",
                        options = listOf("High", "Balanced", "Low", "Off"),
                        selected = uiState.personalizationLevel,
                        onSelect = { viewModel.onPersonalizationLevelChange(it) }
                    )
                    SettingsOptionSelector(
                        title = "Trending Region",
                        options = listOf("India", "Global"),
                        selected = uiState.trendingRegion,
                        onSelect = { viewModel.onTrendingRegionChange(it) }
                    )
                    SettingsOptionSelector(
                        title = "New Release Preferences",
                        options = listOf("Preferred Languages", "All Languages"),
                        selected = uiState.newReleaseLanguage,
                        onSelect = { viewModel.onNewReleaseLanguageChange(it) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsGroupHeader(title = "Discovery Feeds")
                    SettingsToggleRow(
                        title = "Trending Music",
                        subtitle = "Display live breakout and viral charts",
                        checked = uiState.trendingEnabled,
                        onCheckedChange = { viewModel.onTrendingToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "New Releases",
                        subtitle = "Display latest album drops and singles",
                        checked = uiState.newReleasesEnabled,
                        onCheckedChange = { viewModel.onNewReleasesToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Exploration Radar",
                        subtitle = "Include rising indie hits and fresh sounds",
                        checked = uiState.discoveryEnabled,
                        onCheckedChange = { viewModel.onDiscoveryToggle(it) }
                    )
                }
            }

            SettingsSection.LIBRARY -> {
                item {
                    SettingsGroupHeader(title = "Library Categories")
                    SettingsRow(title = "Liked Songs", value = "Synced with Cloud")
                    SettingsRow(title = "Playlists", value = "Custom Playlists")
                    SettingsRow(title = "Albums", value = "Saved Albums")
                    SettingsRow(title = "Artists", value = "Followed Artists")
                    SettingsRow(title = "Recently Played", value = if (uiState.recordListeningHistory) "History Active" else "Paused")
                    SettingsRow(title = "Downloads", value = "Device Storage")
                }
            }

            SettingsSection.NOTIFICATIONS -> {
                item {
                    SettingsGroupHeader(title = "Music Alerts")
                    SettingsToggleRow(
                        title = "New Releases",
                        subtitle = "Get notified when favorite artists drop new tracks",
                        checked = uiState.newReleaseNotifications,
                        onCheckedChange = { viewModel.onNewReleaseNotificationsToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Trending Music",
                        subtitle = "Weekly updates on breakout songs",
                        checked = uiState.trendingNotifications,
                        onCheckedChange = { viewModel.onTrendingNotificationsToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Recommendations",
                        subtitle = "Personalized playlist highlights",
                        checked = uiState.recommendationNotifications,
                        onCheckedChange = { viewModel.onRecommendationNotificationsToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Playlist Activity",
                        subtitle = "Sync and playlist modification notifications",
                        checked = uiState.playlistActivityNotifications,
                        onCheckedChange = { viewModel.onPlaylistActivityNotificationsToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "System Updates",
                        subtitle = "App improvement and release alerts",
                        checked = uiState.systemUpdateNotifications,
                        onCheckedChange = { viewModel.onSystemUpdateNotificationsToggle(it) }
                    )
                }
            }

            SettingsSection.APPEARANCE -> {
                item {
                    SettingsGroupHeader(title = "Theme & Visual Style")
                    SettingsOptionSelector(
                        title = "Theme Mode",
                        options = listOf("Dark", "System", "Light", "AMOLED"),
                        selected = uiState.themeMode,
                        onSelect = { viewModel.onThemeModeChange(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsGroupHeader(title = "Motion & Visual Effects")
                    SettingsToggleRow(
                        title = "Reduce Motion",
                        subtitle = "Minimize 3D carousel parallax and screen transitions",
                        checked = uiState.reduceMotion,
                        onCheckedChange = { viewModel.onReduceMotionToggle(it) }
                    )
                    SettingsOptionSelector(
                        title = "Interface Effects",
                        options = listOf("Subtle Frosted Glass", "Opaque Minimal", "Solid Dark"),
                        selected = uiState.interfaceEffects,
                        onSelect = { viewModel.onInterfaceEffectsChange(it) }
                    )
                }
            }

            SettingsSection.PRIVACY_SECURITY -> {
                item {
                    SettingsGroupHeader(title = "Data & Privacy")
                    SettingsRow(title = "Listening History", value = if (uiState.recordListeningHistory) "Stored Privately On-Device" else "Paused")
                    SettingsRow(title = "Personalized Recommendations", value = if (uiState.personalizedRecommendations) "Encrypted Session Profile" else "Disabled")
                    SettingsRow(title = "Data Usage", value = "Streaming Audio & Cached Metadata")
                    SettingsRow(title = "Active Sessions", value = "1 Active Session")
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsActionRow(
                        title = "Sign Out Everywhere",
                        textColor = AppleMusicRed,
                        onClick = { viewModel.authManager.signOut() }
                    )
                    SettingsActionRow(
                        title = "Delete Account",
                        textColor = DeleteRed,
                        onClick = { showDeleteAccountDialog = true }
                    )
                }
            }

            SettingsSection.STORAGE_DATA -> {
                item {
                    SettingsGroupHeader(title = "Storage Overview")
                    val cacheMb = (uiState.cacheSizeBytes / (1024 * 1024)).coerceAtLeast(0)
                    val downloadedMb = (uiState.downloadedStorageBytes / (1024 * 1024)).coerceAtLeast(0)
                    SettingsRow(title = "Cache Size", value = "$cacheMb MB")
                    SettingsRow(title = "Downloaded Songs", value = "${uiState.downloadedSongsCount} tracks ($downloadedMb MB)")
                    SettingsRow(title = "Offline Storage", value = "High-Quality MP4")
                    
                    SettingsToggleRow(
                        title = "Download over Wi-Fi only",
                        subtitle = "Block downloads on cellular / mobile data",
                        checked = uiState.downloadWifiOnly,
                        onCheckedChange = { viewModel.setDownloadWifiOnly(it) }
                    )

                    SettingsOptionSelector(
                        title = "Network Usage",
                        options = listOf("Allow Mobile Data", "Wi-Fi Only"),
                        selected = uiState.networkUsage,
                        onSelect = { viewModel.onNetworkUsageChange(it) }
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsActionRow(
                        title = "Clear All Downloads (${uiState.downloadedSongsCount})",
                        textColor = AppleMusicPink,
                        onClick = { viewModel.clearAllDownloads() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (uiState.isClearingCache) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = AppleMusicPink,
                            strokeWidth = 2.dp
                        )
                    } else {
                        SettingsActionRow(
                            title = "Clear Cache",
                            textColor = AppleMusicPink,
                            onClick = { viewModel.clearCache(context) }
                        )
                    }
                }
            }

            SettingsSection.APP_UPDATES -> {
                item {
                    SettingsGroupHeader(title = "Current Version")
                    SettingsRow(title = "Musync", value = "Version ${BuildConfig.VERSION_NAME}")
                    SettingsRow(title = "Build", value = "${BuildConfig.VERSION_CODE}")

                    Spacer(modifier = Modifier.height(24.dp))
                    SettingsGroupHeader(title = "Check for Updates")

                    when (val state = updateState) {
                        is UpdateDownloadState.Checking -> {
                            Row(
                                modifier = Modifier.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = AppleMusicPink,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Checking for updates...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextGreySecondary
                                )
                            }
                        }

                        is UpdateDownloadState.Available -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "New Update Available: Musync ${state.info.latestVersion}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = AppleMusicPink
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = state.info.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextGreySecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Download & Install",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AppleMusicPink,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch { appUpdateManager.downloadAndInstallUpdate(state.info.downloadUrl, state.info.fileName) }
                                        }
                                        .padding(vertical = 6.dp)
                                )
                            }
                        }

                        is UpdateDownloadState.Downloading -> {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "Downloading Update: ${state.progressPercent}%",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextWhite
                                )
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Text(
                                text = "Install Update",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = AppleMusicPink,
                                modifier = Modifier
                                    .clickable { appUpdateManager.launchPackageInstaller(state.apkFile) }
                                    .padding(vertical = 8.dp)
                            )
                        }

                        else -> {
                            Column {
                                Text(
                                    text = "You're up to date",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextGreySecondary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                SettingsActionRow(
                                    title = "Check for Updates",
                                    textColor = AppleMusicPink,
                                    onClick = { scope.launch { appUpdateManager.checkForUpdates(false) } }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                    SettingsGroupHeader(title = "What's New in v${BuildConfig.VERSION_NAME}")
                    Text(
                        text = "• Real-Time Song Suggestion Engine & Intelligent Shuffle\n• Live New Releases & Trending Music Discovery Engine\n• Premium Horizontal Playlist Carousel with Motion Physics\n• Ultra-Minimalist Typography-Driven Settings & Account System\n• High-Res 1080p Artwork Pipeline & SWR Caching",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 22.sp
                        ),
                        color = TextGreySecondary
                    )
                }
            }

            SettingsSection.ABOUT -> {
                item {
                    SettingsGroupHeader(title = "About Musync")
                    SettingsRow(title = "Musync", value = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    SettingsActionRow(
                        title = "Instagram",
                        value = "@gowthamchowdary.27",
                        textColor = TextWhite,
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://instagram.com/gowthamchowdary.27"))
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    )
                    SettingsActionRow(
                        title = "Terms of Service",
                        onClick = {
                            showLegalDialog = LegalDialogConfig(
                                title = "Terms of Service",
                                content = "Welcome to Musync. By accessing or using our streaming services, you agree to comply with all applicable terms. Musync streams high-fidelity music directly for personal non-commercial listening. Audio streams and metadata are provided in real-time."
                            )
                        }
                    )
                    SettingsActionRow(
                        title = "Privacy Policy",
                        onClick = {
                            showLegalDialog = LegalDialogConfig(
                                title = "Privacy Policy",
                                content = "Your privacy is paramount. Musync does not sell your private information. Listening history, favorites, and custom playlists are encrypted and stored solely to personalize your audio experience."
                            )
                        }
                    )
                    SettingsActionRow(
                        title = "Open Source Licenses",
                        onClick = {
                            showLegalDialog = LegalDialogConfig(
                                title = "Open Source Licenses",
                                content = "Musync is built with high-performance open-source technologies including Android Jetpack Compose, Media3 ExoPlayer, Kotlin Coroutines, Material 3, and Firebase Authentication."
                            )
                        }
                    )
                    SettingsActionRow(
                        title = "Credits",
                        onClick = {
                            showLegalDialog = LegalDialogConfig(
                                title = "Credits",
                                content = "Engineered & Designed by Gowtham Krishna Chowdary.\nPowered by Google DeepMind Antigravity Advanced Agentic Architecture."
                            )
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // Interactive Dialogs
    if (showEditNameDialog) {
        EditNameDialog(
            currentName = currentUser?.displayName ?: "",
            onConfirm = { newName ->
                scope.launch {
                    val res = viewModel.authManager.updateProfileName(newName)
                    if (res.isSuccess) {
                        viewModel.showToast("Profile name updated to $newName")
                    }
                }
                showEditNameDialog = false
            },
            onDismiss = { showEditNameDialog = false }
        )
    }

    if (showChangePasswordDialog) {
        ChangePasswordDialog(
            onConfirm = { newPassword ->
                scope.launch {
                    val res = viewModel.authManager.updatePassword(newPassword)
                    if (res.isSuccess) {
                        viewModel.showToast("Password changed successfully")
                    }
                }
                showChangePasswordDialog = false
            },
            onDismiss = { showChangePasswordDialog = false }
        )
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onConfirm = {
                scope.launch {
                    viewModel.authManager.deleteAccount()
                    onBack()
                }
                showDeleteAccountDialog = false
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }

    if (showMusicLanguagesDialog) {
        MusicLanguagesDialog(
            selectedLanguages = uiState.preferredMusicLanguages,
            onToggleLanguage = { viewModel.onToggleMusicLanguage(it) },
            onDismiss = { showMusicLanguagesDialog = false }
        )
    }

    showSingleChoiceDialog?.let { config ->
        SingleChoiceDialog(
            title = config.title,
            options = config.options,
            selected = config.selected,
            onSelect = {
                config.onSelect(it)
                showSingleChoiceDialog = null
            },
            onDismiss = { showSingleChoiceDialog = null }
        )
    }

    showLegalDialog?.let { config ->
        LegalInfoDialog(
            title = config.title,
            content = config.content,
            onDismiss = { showLegalDialog = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIALOG CONFIG DATA CLASSES
// ─────────────────────────────────────────────────────────────────────────────

private data class SingleChoiceConfig(
    val title: String,
    val options: List<String>,
    val selected: String,
    val onSelect: (String) -> Unit
)

private data class LegalDialogConfig(
    val title: String,
    val content: String
)

// ─────────────────────────────────────────────────────────────────────────────
// INTERACTIVE DIALOG COMPONENTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EditNameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Edit Profile Name",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Enter your name", color = TextGreyMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGreySecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (name.isNotBlank()) onConfirm(name.trim())
                        }
                    ) {
                        Text("Save", color = AppleMusicPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangePasswordDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Change Password",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = newPassword,
                    onValueChange = { newPassword = it; error = null },
                    placeholder = { Text("New Password (min 6 characters)", color = TextGreyMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; error = null },
                    placeholder = { Text("Confirm New Password", color = TextGreyMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error ?: "", color = DeleteRed, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGreySecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (newPassword.length < 6) {
                                error = "Password must be at least 6 characters"
                            } else if (newPassword != confirmPassword) {
                                error = "Passwords do not match"
                            } else {
                                onConfirm(newPassword.trim())
                            }
                        }
                    ) {
                        Text("Update", color = AppleMusicPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Delete Account",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = DeleteRed
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Are you sure you want to delete your Musync account? All your favorites, playlists, and listening preferences will be permanently removed. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = TextGreySecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGreySecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text("Delete", color = DeleteRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicLanguagesDialog(
    selectedLanguages: Set<String>,
    onToggleLanguage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf("Telugu", "Hindi", "English", "Tamil", "Kannada", "Malayalam", "Punjabi", "Marathi", "Bengali")

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Preferred Music Languages",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(languages.size) { index ->
                        val lang = languages[index]
                        val isChecked = selectedLanguages.contains(lang)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleLanguage(lang) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = lang, style = MaterialTheme.typography.bodyLarge, color = TextWhite)
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { onToggleLanguage(lang) },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppleMusicPink,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Done", color = AppleMusicPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.height(260.dp)) {
                    items(options.size) { index ->
                        val opt = options[index]
                        val isSelected = opt.equals(selected, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(opt) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = opt,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) AppleMusicPink else TextWhite
                            )
                            if (isSelected) {
                                Text("✓", color = AppleMusicPink, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextGreySecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalInfoDialog(
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    ),
                    color = TextGreySecondary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = AppleMusicPink, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MINIMAL REUSABLE TYPOGRAPHY ROWS (No icons, high contrast, generous whitespace)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        ),
        color = TextGreyMuted,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                color = TextWhite
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = TextGreySecondary,
                textAlign = TextAlign.End
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0x14FFFFFF))
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    value: String? = null,
    textColor: Color = TextWhite,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = textColor
            )
            if (!value.isNullOrBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = AppleMusicPink,
                    textAlign = TextAlign.End
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0x14FFFFFF))
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
                    color = TextWhite
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextGreySecondary
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onCheckedChange(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AppleMusicPink,
                    uncheckedThumbColor = TextGreyMuted,
                    uncheckedTrackColor = Color(0xFF222226)
                )
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0x14FFFFFF))
        )
    }
}

@Composable
private fun SettingsOptionSelector(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.Normal),
            color = TextWhite
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                val isSel = opt.equals(selected, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0x30FFFFFF) else Color(0x12FFFFFF))
                        .clickable {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            onSelect(opt)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = opt.replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = if (isSel) AppleMusicPink else TextGreySecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0x14FFFFFF))
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MINIMAL EMAIL + PASSWORD AUTH MODAL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MinimalAuthDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    var isSignUp by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val authLoading by viewModel.authManager.authLoading.collectAsState()
    val authError by viewModel.authManager.authError.collectAsState()
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xF518181B))
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isSignUp) "Create Account" else "Sign In",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (isSignUp) {
                    TextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        placeholder = { Text("Name", color = TextGreyMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x18FFFFFF)),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("Email", color = TextGreyMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = TextGreyMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x18FFFFFF)),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )

                if (!authError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = authError ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = DeleteRed,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (authLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = AppleMusicPink,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = if (isSignUp) "Create Account" else "Sign In",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        ),
                        color = AppleMusicPink,
                        modifier = Modifier
                            .clickable {
                                scope.launch {
                                    if (isSignUp) {
                                        val res = viewModel.authManager.signUpWithEmail(email, password, displayName)
                                        if (res.isSuccess) onDismiss()
                                    } else {
                                        val res = viewModel.authManager.signInWithEmail(email, password)
                                        if (res.isSuccess) onDismiss()
                                    }
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(
                        text = if (isSignUp) "Already have an account? Sign In" else "New to Musync? Create Account",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = TextGreySecondary
                    )
                }
            }
        }
    }
}
