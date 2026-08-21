package com.musync.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.data.remote.UniversalMusicProvider
import com.musync.app.data.local.datastore.PreferencesManager
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.AudioEffectManager
import com.musync.app.playback.BeatHapticManager
import com.musync.app.playback.HapticIntensity
import com.musync.app.playback.SoundEngine
import com.musync.app.playback.SoundEngineRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, ERROR
}

data class SettingsUiState(
    // Backend API & Connection
    val customApiUrl: String = "",
    val apiKey: String = "",
    val isCustomApiConfigured: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val statusMessage: String? = null,

    // General
    val appLanguage: String = "English",
    val region: String = "India",
    val preferredMusicLanguages: Set<String> = setOf("Telugu", "Hindi", "English"),
    val allowExplicitContent: Boolean = true,
    val defaultLandingPage: String = "Home",
    val defaultPlaybackBehavior: String = "Resume",

    // Playback
    val autoplay: Boolean = true,
    val intelligentShuffle: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val continuePlaying: Boolean = true,
    val queueBehavior: String = "Play Next",
    val recordListeningHistory: Boolean = true,

    // Audio & DSP
    val audioQuality: String = "high",
    val downloadQuality: String = "high",
    val equalizerPreset: String = "Bass Boost",
    val soundEngineId: String = "dolby",
    val soundEngineTitle: String = "Dolby Atmos 3D",
    val audioNormalization: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.OFF,

    // Music & Discovery
    val personalizedRecommendations: Boolean = true,
    val trendingRegion: String = "India",
    val newReleaseLanguage: String = "Preferred Languages",
    val personalizationLevel: String = "Balanced",
    val trendingEnabled: Boolean = true,
    val newReleasesEnabled: Boolean = true,
    val discoveryEnabled: Boolean = true,
    val preferredArtists: Set<String> = emptySet(),
    val favoriteGenres: Set<String> = emptySet(),

    // Notifications
    val newReleaseNotifications: Boolean = true,
    val trendingNotifications: Boolean = true,
    val recommendationNotifications: Boolean = true,
    val playlistActivityNotifications: Boolean = true,
    val systemUpdateNotifications: Boolean = true,

    // Appearance
    val themeMode: String = "Dark",
    val reduceMotion: Boolean = false,
    val interfaceEffects: String = "Subtle Frosted Glass",

    // Storage & Network
    val networkUsage: String = "Allow Mobile Data",
    val cacheSizeBytes: Long = 0L,
    val isClearingCache: Boolean = false,

    // Operation Feedback
    val toastMessage: String? = null
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val musicRepository: MusicRepository,
    private val universalMusicProvider: UniversalMusicProvider,
    private val beatHapticManager: BeatHapticManager,
    val authManager: com.musync.app.auth.AuthManager,
    val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager,
    val audioEffectManager: AudioEffectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            apiKey = preferencesManager.getApiKey() ?: "",
            hapticIntensity = beatHapticManager.getIntensity(),
            audioQuality = preferencesManager.getAudioQuality(),
            downloadQuality = preferencesManager.getDownloadQuality(),
            appLanguage = preferencesManager.getAppLanguage(),
            region = preferencesManager.getRegion(),
            preferredMusicLanguages = preferencesManager.getMusicLanguages(),
            allowExplicitContent = preferencesManager.getAllowExplicitContent(),
            defaultLandingPage = preferencesManager.getDefaultLandingPage(),
            defaultPlaybackBehavior = preferencesManager.getDefaultPlaybackBehavior(),
            autoplay = preferencesManager.getAutoplay(),
            intelligentShuffle = preferencesManager.getIntelligentShuffle(),
            gaplessPlayback = preferencesManager.getGaplessPlayback(),
            crossfadeSeconds = preferencesManager.getCrossfadeSeconds(),
            continuePlaying = preferencesManager.getContinuePlaying(),
            queueBehavior = preferencesManager.getQueueBehavior(),
            recordListeningHistory = preferencesManager.getRecordListeningHistory(),
            equalizerPreset = preferencesManager.getEqualizerPreset(),
            soundEngineId = preferencesManager.getSoundEngineId(),
            audioNormalization = preferencesManager.getAudioNormalization(),
            personalizedRecommendations = preferencesManager.getPersonalizedRecommendations(),
            trendingRegion = preferencesManager.getTrendingRegion(),
            newReleaseLanguage = preferencesManager.getNewReleaseLanguage(),
            personalizationLevel = preferencesManager.getPersonalizationLevel(),
            themeMode = preferencesManager.getThemeMode(),
            reduceMotion = preferencesManager.getReduceMotion(),
            networkUsage = preferencesManager.getNetworkUsage()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Collect DataStore Flows to keep StateFlow strictly in sync with storage
        viewModelScope.launch {
            preferencesManager.baseUrl.collect { url ->
                val isConfigured = url.isNotBlank() && url != "none"
                _uiState.update {
                    it.copy(
                        customApiUrl = if (url == "none") "" else url,
                        isCustomApiConfigured = isConfigured
                    )
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.appLanguage.collect { lang ->
                _uiState.update { it.copy(appLanguage = lang) }
            }
        }
        viewModelScope.launch {
            preferencesManager.region.collect { reg ->
                _uiState.update { it.copy(region = reg) }
            }
        }
        viewModelScope.launch {
            preferencesManager.musicLanguages.collect { langs ->
                _uiState.update { it.copy(preferredMusicLanguages = langs) }
            }
        }
        viewModelScope.launch {
            preferencesManager.allowExplicitContent.collect { allow ->
                _uiState.update { it.copy(allowExplicitContent = allow) }
            }
        }
        viewModelScope.launch {
            preferencesManager.defaultLandingPage.collect { page ->
                _uiState.update { it.copy(defaultLandingPage = page) }
            }
        }
        viewModelScope.launch {
            preferencesManager.defaultPlaybackBehavior.collect { beh ->
                _uiState.update { it.copy(defaultPlaybackBehavior = beh) }
            }
        }
        viewModelScope.launch {
            preferencesManager.autoplay.collect { auto ->
                _uiState.update { it.copy(autoplay = auto) }
            }
        }
        viewModelScope.launch {
            preferencesManager.intelligentShuffle.collect { shuf ->
                _uiState.update { it.copy(intelligentShuffle = shuf) }
            }
        }
        viewModelScope.launch {
            preferencesManager.gaplessPlayback.collect { gap ->
                _uiState.update { it.copy(gaplessPlayback = gap) }
            }
        }
        viewModelScope.launch {
            preferencesManager.crossfadeSeconds.collect { xfade ->
                _uiState.update { it.copy(crossfadeSeconds = xfade) }
            }
        }
        viewModelScope.launch {
            preferencesManager.continuePlaying.collect { cont ->
                _uiState.update { it.copy(continuePlaying = cont) }
            }
        }
        viewModelScope.launch {
            preferencesManager.queueBehavior.collect { qb ->
                _uiState.update { it.copy(queueBehavior = qb) }
            }
        }
        viewModelScope.launch {
            preferencesManager.recordListeningHistory.collect { hist ->
                _uiState.update { it.copy(recordListeningHistory = hist) }
            }
        }
        viewModelScope.launch {
            preferencesManager.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
            }
        }
        viewModelScope.launch {
            preferencesManager.downloadQuality.collect { dlQuality ->
                _uiState.update { it.copy(downloadQuality = dlQuality) }
            }
        }
        viewModelScope.launch {
            preferencesManager.equalizerPreset.collect { eq ->
                _uiState.update { it.copy(equalizerPreset = eq) }
            }
        }
        viewModelScope.launch {
            preferencesManager.audioNormalization.collect { norm ->
                _uiState.update { it.copy(audioNormalization = norm) }
            }
        }
        viewModelScope.launch {
            preferencesManager.personalizedRecommendations.collect { recs ->
                _uiState.update { it.copy(personalizedRecommendations = recs) }
            }
        }
        viewModelScope.launch {
            preferencesManager.trendingRegion.collect { tr ->
                _uiState.update { it.copy(trendingRegion = tr) }
            }
        }
        viewModelScope.launch {
            preferencesManager.newReleaseLanguage.collect { nrl ->
                _uiState.update { it.copy(newReleaseLanguage = nrl) }
            }
        }
        viewModelScope.launch {
            preferencesManager.personalizationLevel.collect { pl ->
                _uiState.update { it.copy(personalizationLevel = pl) }
            }
        }
        viewModelScope.launch {
            preferencesManager.trendingEnabled.collect { te ->
                _uiState.update { it.copy(trendingEnabled = te) }
            }
        }
        viewModelScope.launch {
            preferencesManager.newReleasesEnabled.collect { nre ->
                _uiState.update { it.copy(newReleasesEnabled = nre) }
            }
        }
        viewModelScope.launch {
            preferencesManager.discoveryEnabled.collect { de ->
                _uiState.update { it.copy(discoveryEnabled = de) }
            }
        }
        viewModelScope.launch {
            preferencesManager.newReleaseNotifications.collect { nrn ->
                _uiState.update { it.copy(newReleaseNotifications = nrn) }
            }
        }
        viewModelScope.launch {
            preferencesManager.trendingNotifications.collect { tn ->
                _uiState.update { it.copy(trendingNotifications = tn) }
            }
        }
        viewModelScope.launch {
            preferencesManager.recommendationNotifications.collect { rn ->
                _uiState.update { it.copy(recommendationNotifications = rn) }
            }
        }
        viewModelScope.launch {
            preferencesManager.playlistActivityNotifications.collect { pan ->
                _uiState.update { it.copy(playlistActivityNotifications = pan) }
            }
        }
        viewModelScope.launch {
            preferencesManager.systemUpdateNotifications.collect { sun ->
                _uiState.update { it.copy(systemUpdateNotifications = sun) }
            }
        }
        viewModelScope.launch {
            preferencesManager.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            preferencesManager.reduceMotion.collect { rm ->
                _uiState.update { it.copy(reduceMotion = rm) }
            }
        }
        viewModelScope.launch {
            preferencesManager.interfaceEffects.collect { ie ->
                _uiState.update { it.copy(interfaceEffects = ie) }
            }
        }
        viewModelScope.launch {
            preferencesManager.networkUsage.collect { nu ->
                _uiState.update { it.copy(networkUsage = nu) }
            }
        }
        viewModelScope.launch {
            preferencesManager.hapticIntensity.collect { intensityName ->
                val intensity = try {
                    HapticIntensity.valueOf(intensityName)
                } catch (_: Exception) {
                    HapticIntensity.OFF
                }
                beatHapticManager.setIntensity(intensity)
                _uiState.update { it.copy(hapticIntensity = intensity) }
            }
        }
    }

    fun showToast(msg: String) {
        _uiState.update { it.copy(toastMessage = msg) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ──────────────────────────────────────────────────────────────
    // GENERAL SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onAppLanguageChange(lang: String) {
        _uiState.update { it.copy(appLanguage = lang) }
        viewModelScope.launch { preferencesManager.setAppLanguage(lang) }
    }

    fun onRegionChange(reg: String) {
        _uiState.update { it.copy(region = reg) }
        viewModelScope.launch { preferencesManager.setRegion(reg) }
    }

    fun onToggleMusicLanguage(lang: String) {
        val current = _uiState.value.preferredMusicLanguages.toMutableSet()
        if (current.contains(lang)) {
            if (current.size > 1) current.remove(lang) // keep at least 1
        } else {
            current.add(lang)
        }
        _uiState.update { it.copy(preferredMusicLanguages = current) }
        viewModelScope.launch { preferencesManager.setMusicLanguages(current) }
    }

    fun onExplicitContentToggle(allow: Boolean) {
        _uiState.update { it.copy(allowExplicitContent = allow) }
        viewModelScope.launch { preferencesManager.setAllowExplicitContent(allow) }
    }

    fun onDefaultLandingPageChange(page: String) {
        _uiState.update { it.copy(defaultLandingPage = page) }
        viewModelScope.launch { preferencesManager.setDefaultLandingPage(page) }
    }

    fun onDefaultPlaybackBehaviorChange(behavior: String) {
        _uiState.update { it.copy(defaultPlaybackBehavior = behavior) }
        viewModelScope.launch { preferencesManager.setDefaultPlaybackBehavior(behavior) }
    }

    // ──────────────────────────────────────────────────────────────
    // PLAYBACK SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onAutoplayToggle(enabled: Boolean) {
        _uiState.update { it.copy(autoplay = enabled) }
        viewModelScope.launch { preferencesManager.setAutoplay(enabled) }
    }

    fun onIntelligentShuffleToggle(enabled: Boolean) {
        _uiState.update { it.copy(intelligentShuffle = enabled) }
        viewModelScope.launch { preferencesManager.setIntelligentShuffle(enabled) }
    }

    fun onGaplessPlaybackToggle(enabled: Boolean) {
        _uiState.update { it.copy(gaplessPlayback = enabled) }
        viewModelScope.launch { preferencesManager.setGaplessPlayback(enabled) }
    }

    fun onCrossfadeSecondsChange(seconds: Int) {
        _uiState.update { it.copy(crossfadeSeconds = seconds) }
        viewModelScope.launch { preferencesManager.setCrossfadeSeconds(seconds) }
    }

    fun onContinuePlayingToggle(enabled: Boolean) {
        _uiState.update { it.copy(continuePlaying = enabled) }
        viewModelScope.launch { preferencesManager.setContinuePlaying(enabled) }
    }

    fun onQueueBehaviorChange(behavior: String) {
        _uiState.update { it.copy(queueBehavior = behavior) }
        viewModelScope.launch { preferencesManager.setQueueBehavior(behavior) }
    }

    fun onRecordHistoryToggle(enabled: Boolean) {
        _uiState.update { it.copy(recordListeningHistory = enabled) }
        viewModelScope.launch { preferencesManager.setRecordListeningHistory(enabled) }
    }

    // ──────────────────────────────────────────────────────────────
    // AUDIO & DSP SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onAudioQualityChange(quality: String) {
        _uiState.update { it.copy(audioQuality = quality) }
        viewModelScope.launch {
            preferencesManager.setAudioQuality(quality)
            universalMusicProvider.updateAudioQuality(quality)
        }
    }

    fun onDownloadQualityChange(quality: String) {
        _uiState.update { it.copy(downloadQuality = quality) }
        viewModelScope.launch { preferencesManager.setDownloadQuality(quality) }
    }

    fun onEqualizerPresetChange(preset: String) {
        _uiState.update { it.copy(equalizerPreset = preset) }
        audioEffectManager.applyEqualizerPreset(preset)
    }

    fun onSoundEngineChange(engine: SoundEngine) {
        audioEffectManager.selectEngine(engine)
        _uiState.update {
            it.copy(
                soundEngineId = engine.id,
                soundEngineTitle = engine.title
            )
        }
    }

    fun onAudioNormalizationToggle(enabled: Boolean) {
        _uiState.update { it.copy(audioNormalization = enabled) }
        audioEffectManager.setAudioNormalization(enabled)
    }

    fun onHapticIntensityChange(intensity: HapticIntensity) {
        beatHapticManager.setIntensity(intensity)
        _uiState.update { it.copy(hapticIntensity = intensity) }
        beatHapticManager.triggerBeat()
        viewModelScope.launch {
            preferencesManager.setHapticIntensity(intensity.name)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // MUSIC & DISCOVERY SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onPersonalizedRecommendationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(personalizedRecommendations = enabled) }
        viewModelScope.launch { preferencesManager.setPersonalizedRecommendations(enabled) }
    }

    fun onTrendingRegionChange(region: String) {
        _uiState.update { it.copy(trendingRegion = region) }
        viewModelScope.launch { preferencesManager.setTrendingRegion(region) }
    }

    fun onNewReleaseLanguageChange(language: String) {
        _uiState.update { it.copy(newReleaseLanguage = language) }
        viewModelScope.launch { preferencesManager.setNewReleaseLanguage(language) }
    }

    fun onPersonalizationLevelChange(level: String) {
        _uiState.update { it.copy(personalizationLevel = level) }
        viewModelScope.launch { preferencesManager.setPersonalizationLevel(level) }
    }

    fun onTrendingToggle(enabled: Boolean) {
        _uiState.update { it.copy(trendingEnabled = enabled) }
        viewModelScope.launch { preferencesManager.setTrendingEnabled(enabled) }
    }

    fun onNewReleasesToggle(enabled: Boolean) {
        _uiState.update { it.copy(newReleasesEnabled = enabled) }
        viewModelScope.launch { preferencesManager.setNewReleasesEnabled(enabled) }
    }

    fun onDiscoveryToggle(enabled: Boolean) {
        _uiState.update { it.copy(discoveryEnabled = enabled) }
        viewModelScope.launch { preferencesManager.setDiscoveryEnabled(enabled) }
    }

    // ──────────────────────────────────────────────────────────────
    // NOTIFICATIONS SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onNewReleaseNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(newReleaseNotifications = enabled) }
        viewModelScope.launch { preferencesManager.setNewReleaseNotifications(enabled) }
    }

    fun onTrendingNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(trendingNotifications = enabled) }
        viewModelScope.launch { preferencesManager.setTrendingNotifications(enabled) }
    }

    fun onRecommendationNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(recommendationNotifications = enabled) }
        viewModelScope.launch { preferencesManager.setRecommendationNotifications(enabled) }
    }

    fun onPlaylistActivityNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(playlistActivityNotifications = enabled) }
        viewModelScope.launch { preferencesManager.setPlaylistActivityNotifications(enabled) }
    }

    fun onSystemUpdateNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(systemUpdateNotifications = enabled) }
        viewModelScope.launch { preferencesManager.setSystemUpdateNotifications(enabled) }
    }

    // ──────────────────────────────────────────────────────────────
    // APPEARANCE SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onThemeModeChange(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun onReduceMotionToggle(enabled: Boolean) {
        _uiState.update { it.copy(reduceMotion = enabled) }
        viewModelScope.launch { preferencesManager.setReduceMotion(enabled) }
    }

    fun onInterfaceEffectsChange(effects: String) {
        _uiState.update { it.copy(interfaceEffects = effects) }
        viewModelScope.launch { preferencesManager.setInterfaceEffects(effects) }
    }

    // ──────────────────────────────────────────────────────────────
    // STORAGE & NETWORK SECTION ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onNetworkUsageChange(usage: String) {
        _uiState.update { it.copy(networkUsage = usage) }
        viewModelScope.launch { preferencesManager.setNetworkUsage(usage) }
    }

    fun computeCacheSize(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var size = 0L
                val cacheDir = context.cacheDir
                if (cacheDir.exists()) {
                    size += getFolderSize(cacheDir)
                }
                val codeCacheDir = context.codeCacheDir
                if (codeCacheDir.exists()) {
                    size += getFolderSize(codeCacheDir)
                }
                _uiState.update { it.copy(cacheSizeBytes = size) }
            } catch (_: Exception) {}
        }
    }

    fun clearCache(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isClearingCache = true) }
            try {
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
            } catch (_: Exception) {}
            _uiState.update { it.copy(cacheSizeBytes = 0L, isClearingCache = false) }
        }
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) getFolderSize(file) else file.length()
        }
        return size
    }

    // ──────────────────────────────────────────────────────────────
    // CUSTOM API / BACKEND ACTIONS
    // ──────────────────────────────────────────────────────────────

    fun onCustomApiUrlChange(url: String) {
        _uiState.update { it.copy(customApiUrl = url, connectionStatus = ConnectionStatus.IDLE) }
    }

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(apiKey = key, connectionStatus = ConnectionStatus.IDLE) }
    }

    fun saveCustomApi() {
        val state = _uiState.value
        val url = state.customApiUrl.trim()
        val key = state.apiKey.trim()

        if (url.isBlank()) {
            clearCustomApi()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = ConnectionStatus.TESTING, statusMessage = "Connecting to custom music endpoint...") }

            preferencesManager.setBaseUrl(url)
            preferencesManager.setApiKey(key)
            universalMusicProvider.updateConfiguration(url, key)

            val success = musicRepository.testConnection(url, key)
            if (success) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.SUCCESS,
                        isCustomApiConfigured = true,
                        statusMessage = "API Connected successfully ✓"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.ERROR,
                        isCustomApiConfigured = false,
                        statusMessage = "Connection failed. Please check endpoint URL."
                    )
                }
            }
        }
    }

    fun clearCustomApi() {
        viewModelScope.launch {
            preferencesManager.setBaseUrl("none")
            preferencesManager.setApiKey(null)
            universalMusicProvider.updateConfiguration("", null)
            _uiState.update {
                it.copy(
                    customApiUrl = "",
                    apiKey = "",
                    isCustomApiConfigured = false,
                    connectionStatus = ConnectionStatus.IDLE,
                    statusMessage = null
                )
            }
        }
    }

    class Factory(
        private val preferencesManager: PreferencesManager,
        private val musicRepository: MusicRepository,
        private val universalMusicProvider: UniversalMusicProvider,
        private val beatHapticManager: BeatHapticManager,
        private val authManager: com.musync.app.auth.AuthManager,
        private val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager,
        private val audioEffectManager: AudioEffectManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesManager,
                musicRepository,
                universalMusicProvider,
                beatHapticManager,
                authManager,
                cloudSyncManager,
                audioEffectManager
            ) as T
        }
    }
}
