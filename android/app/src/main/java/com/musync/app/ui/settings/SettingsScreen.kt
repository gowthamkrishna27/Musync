package com.musync.app.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val scope = rememberCoroutineScope()
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
                    // Header Back Navigation & Title
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 24.dp),
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

                    // Profile / Account Header
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
                                .padding(bottom = 32.dp)
                        ) {
                            Text(
                                text = currentUser?.displayName ?: "Gowtham Krishna",
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    letterSpacing = (-0.5).sp
                                ),
                                color = TextWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = currentUser?.email ?: "email@example.com",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    color = TextGreySecondary
                                )
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

                    // The 12 Sections (Pure text navigation, no icons)
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
                    SettingsGroupHeader(title = "Profile")
                    SettingsRow(title = "Name", value = currentUser?.displayName ?: "Gowtham Krishna")
                    SettingsRow(title = "Email", value = currentUser?.email ?: "email@example.com")
                    SettingsRow(title = "Language", value = "English")
                    SettingsRow(title = "Region", value = "India")
                    SettingsRow(title = "Preferred Music Languages", value = "Telugu, Hindi, English")
                }
            }

            SettingsSection.ACCOUNT -> {
                item {
                    SettingsGroupHeader(title = "Personal Information")
                    SettingsRow(title = "Name", value = currentUser?.displayName ?: "Gowtham Krishna")
                    SettingsRow(title = "Email", value = currentUser?.email ?: "email@example.com")
                    SettingsActionRow(title = "Change Password", onClick = {
                        currentUser?.email?.let { email ->
                            scope.launch { viewModel.authManager.sendPasswordReset(email) }
                        }
                    })
                    SettingsRow(title = "Active Sessions", value = "1 Device Active")

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
                            onClick = { scope.launch { viewModel.authManager.deleteAccount() } }
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
                        subtitle = "Keep music playing when queue finishes",
                        checked = uiState.autoplay,
                        onCheckedChange = { viewModel.onAutoplayToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Intelligent Shuffle",
                        subtitle = "Session-aware probabilistic queue ordering",
                        checked = uiState.intelligentShuffle,
                        onCheckedChange = { viewModel.onIntelligentShuffleToggle(it) }
                    )
                    SettingsToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Eliminate pauses between consecutive tracks",
                        checked = uiState.gaplessPlayback,
                        onCheckedChange = { viewModel.onGaplessPlaybackToggle(it) }
                    )
                    SettingsRow(title = "Crossfade", value = if (uiState.crossfadeSeconds > 0) "${uiState.crossfadeSeconds}s" else "Off")
                    SettingsToggleRow(
                        title = "Continue Playing",
                        subtitle = "Remember last played track position across sessions",
                        checked = uiState.continuePlaying,
                        onCheckedChange = {}
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
                    SettingsGroupHeader(title = "Sound Enhancements")
                    SettingsRow(title = "Equalizer", value = "Bass Boost")
                    SettingsRow(title = "Dolby Atmos", value = "Spatial Audio (Dolby Atmos)")
                    SettingsToggleRow(
                        title = "Audio Normalization",
                        subtitle = "Maintain consistent volume levels across songs",
                        checked = uiState.audioNormalization,
                        onCheckedChange = {}
                    )
                    SettingsRow(title = "Audio Effects", value = "Dolby Music Dynamic")
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
                    SettingsOptionSelector(
                        title = "Recommendation Personalization",
                        options = listOf("High", "Balanced", "Low", "Off"),
                        selected = uiState.personalizationLevel,
                        onSelect = { viewModel.onPersonalizationLevelChange(it) }
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
                    SettingsRow(title = "Recently Played", value = "History Enabled")
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
                        title = "System Updates",
                        subtitle = "App improvement and release alerts",
                        checked = true,
                        onCheckedChange = {}
                    )
                }
            }

            SettingsSection.APPEARANCE -> {
                item {
                    SettingsGroupHeader(title = "Theme")
                    SettingsOptionSelector(
                        title = "Appearance Theme",
                        options = listOf("Dark", "System", "Light"),
                        selected = uiState.themeMode,
                        onSelect = { viewModel.onThemeModeChange(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsGroupHeader(title = "Motion & Visuals")
                    SettingsToggleRow(
                        title = "Reduce Motion",
                        subtitle = "Minimize carousel animations and transitions",
                        checked = uiState.reduceMotion,
                        onCheckedChange = { viewModel.onReduceMotionToggle(it) }
                    )
                    SettingsRow(title = "Interface Effects", value = "Subtle Frosted Glass")
                }
            }

            SettingsSection.PRIVACY_SECURITY -> {
                item {
                    SettingsGroupHeader(title = "Data & Privacy")
                    SettingsRow(title = "Listening History", value = "Stored Privately On-Device")
                    SettingsRow(title = "Personalized Recommendations", value = "Encrypted Session Profile")
                    SettingsRow(title = "Data Usage", value = "Streaming Audio Only")
                    SettingsRow(title = "Active Sessions", value = "1 Active Session")
                    Spacer(modifier = Modifier.height(20.dp))
                    SettingsActionRow(
                        title = "Sign Out Everywhere",
                        textColor = AppleMusicRed,
                        onClick = { viewModel.authManager.signOut() }
                    )
                }
            }

            SettingsSection.STORAGE_DATA -> {
                item {
                    SettingsGroupHeader(title = "Storage Overview")
                    val cacheMb = (uiState.cacheSizeBytes / (1024 * 1024)).coerceAtLeast(0)
                    SettingsRow(title = "Cache Size", value = "$cacheMb MB")
                    SettingsRow(title = "Downloaded Music", value = "0 MB")
                    SettingsRow(title = "Offline Data", value = "Catalog & Session DB")
                    SettingsRow(title = "Network Usage", value = "Wi-Fi & Mobile Data")

                    Spacer(modifier = Modifier.height(24.dp))
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
                    SettingsActionRow(title = "Terms of Service", onClick = {})
                    SettingsActionRow(title = "Privacy Policy", onClick = {})
                    SettingsActionRow(title = "Open Source Licenses", onClick = {})
                    SettingsRow(title = "Credits", value = "Built with Google DeepMind Antigravity")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(60.dp))
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal
            ),
            color = textColor
        )
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
