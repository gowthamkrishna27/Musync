package com.musync.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.data.remote.UniversalMusicProvider
import com.musync.app.data.local.datastore.PreferencesManager
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.BeatHapticManager
import com.musync.app.playback.HapticIntensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, ERROR
}

data class SettingsUiState(
    val customApiUrl: String = "",
    val apiKey: String = "",
    val isCustomApiConfigured: Boolean = false,
    val audioQuality: String = "high",
    val downloadQuality: String = "high",
    val hapticIntensity: HapticIntensity = HapticIntensity.OFF,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val statusMessage: String? = null,

    // Playback
    val autoplay: Boolean = true,
    val intelligentShuffle: Boolean = true,
    val gaplessPlayback: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val continuePlaying: Boolean = true,

    // Audio & Dolby
    val audioNormalization: Boolean = true,
    val dolbyAtmosEnabled: Boolean = true,

    // Music & Discovery
    val personalizedRecommendations: Boolean = true,
    val trendingRegion: String = "India",
    val newReleaseLanguage: String = "Preferred Languages",
    val personalizationLevel: String = "Balanced",

    // Notifications
    val newReleaseNotifications: Boolean = true,
    val trendingNotifications: Boolean = true,
    val recommendationNotifications: Boolean = true,

    // Appearance
    val themeMode: String = "Dark",
    val reduceMotion: Boolean = false,

    // Storage
    val cacheSizeBytes: Long = 0L,
    val isClearingCache: Boolean = false
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val musicRepository: MusicRepository,
    private val universalMusicProvider: UniversalMusicProvider,
    private val beatHapticManager: BeatHapticManager,
    val authManager: com.musync.app.auth.AuthManager,
    val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            apiKey = preferencesManager.getApiKey() ?: "",
            hapticIntensity = beatHapticManager.getIntensity()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
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
            preferencesManager.audioQuality.collect { quality ->
                _uiState.update { it.copy(audioQuality = quality) }
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

    fun onCustomApiUrlChange(url: String) {
        _uiState.update { it.copy(customApiUrl = url, connectionStatus = ConnectionStatus.IDLE) }
    }

    fun onApiKeyChange(key: String) {
        _uiState.update { it.copy(apiKey = key, connectionStatus = ConnectionStatus.IDLE) }
    }

    fun onAudioQualityChange(quality: String) {
        _uiState.update { it.copy(audioQuality = quality) }
        viewModelScope.launch {
            preferencesManager.setAudioQuality(quality)
        }
    }

    fun onDownloadQualityChange(quality: String) {
        _uiState.update { it.copy(downloadQuality = quality) }
    }

    fun onAutoplayToggle(enabled: Boolean) {
        _uiState.update { it.copy(autoplay = enabled) }
    }

    fun onIntelligentShuffleToggle(enabled: Boolean) {
        _uiState.update { it.copy(intelligentShuffle = enabled) }
    }

    fun onGaplessPlaybackToggle(enabled: Boolean) {
        _uiState.update { it.copy(gaplessPlayback = enabled) }
    }

    fun onCrossfadeSecondsChange(seconds: Int) {
        _uiState.update { it.copy(crossfadeSeconds = seconds) }
    }

    fun onPersonalizedRecommendationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(personalizedRecommendations = enabled) }
    }

    fun onTrendingRegionChange(region: String) {
        _uiState.update { it.copy(trendingRegion = region) }
    }

    fun onNewReleaseLanguageChange(language: String) {
        _uiState.update { it.copy(newReleaseLanguage = language) }
    }

    fun onPersonalizationLevelChange(level: String) {
        _uiState.update { it.copy(personalizationLevel = level) }
    }

    fun onNewReleaseNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(newReleaseNotifications = enabled) }
    }

    fun onTrendingNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(trendingNotifications = enabled) }
    }

    fun onRecommendationNotificationsToggle(enabled: Boolean) {
        _uiState.update { it.copy(recommendationNotifications = enabled) }
    }

    fun onThemeModeChange(mode: String) {
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun onReduceMotionToggle(enabled: Boolean) {
        _uiState.update { it.copy(reduceMotion = enabled) }
    }

    fun onHapticIntensityChange(intensity: HapticIntensity) {
        beatHapticManager.setIntensity(intensity)
        _uiState.update { it.copy(hapticIntensity = intensity) }
        beatHapticManager.triggerBeat()
        viewModelScope.launch {
            preferencesManager.setHapticIntensity(intensity.name)
        }
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
        private val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, musicRepository, universalMusicProvider, beatHapticManager, authManager, cloudSyncManager) as T
        }
    }
}
