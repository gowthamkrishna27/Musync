package com.musync.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.data.api.UniversalMusicProvider
import com.musync.app.data.datastore.PreferencesManager
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.BeatHapticManager
import com.musync.app.playback.HapticIntensity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionStatus {
    IDLE, TESTING, SUCCESS, ERROR
}

data class SettingsUiState(
    val customApiUrl: String = "",
    val apiKey: String = "",
    val isCustomApiConfigured: Boolean = false,
    val audioQuality: String = "high",
    val hapticIntensity: HapticIntensity = HapticIntensity.OFF,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val musicRepository: MusicRepository,
    private val universalMusicProvider: UniversalMusicProvider,
    private val beatHapticManager: BeatHapticManager
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
        private val beatHapticManager: BeatHapticManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(preferencesManager, musicRepository, universalMusicProvider, beatHapticManager) as T
        }
    }
}

