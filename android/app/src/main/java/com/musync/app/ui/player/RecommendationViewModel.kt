package com.musync.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.PlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendationUiState(
    val currentTrackId: String? = null,
    val recommendations: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Dedicated RecommendationViewModel that observes current playing track,
 * applies a non-blocking debounce (250ms), cancels stale requests on track transitions,
 * and maintains suggestion state completely isolated from ExoPlayer audio playback.
 */
class RecommendationViewModel(
    private val playbackManager: PlaybackManager,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecommendationUiState())
    val uiState: StateFlow<RecommendationUiState> = _uiState.asStateFlow()

    private var recommendationJob: Job? = null

    init {
        observeCurrentTrack()
    }

    private fun observeCurrentTrack() {
        viewModelScope.launch {
            playbackManager.playbackState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { trackId ->
                    handleTrackChanged(trackId)
                }
        }
    }

    private fun handleTrackChanged(trackId: String?) {
        // Cancel any pending / in-flight recommendation request so stale responses are discarded
        recommendationJob?.cancel()

        if (trackId.isNullOrBlank()) {
            _uiState.value = RecommendationUiState(currentTrackId = null, recommendations = emptyList())
            return
        }

        // Launch background loading with non-blocking 250ms debounce
        recommendationJob = viewModelScope.launch {
            _uiState.update { it.copy(currentTrackId = trackId, isLoading = true, errorMessage = null) }

            // Debounce track changes (rapid song skipping protection)
            delay(250)

            val result = musicRepository.getRecommendations(trackId, limit = 6)
            result.onSuccess { tracks ->
                // Filter out current track explicitly just in case
                val filtered = tracks.filter { it.id != trackId && it.id.removePrefix("yt_") != trackId.removePrefix("yt_") }
                _uiState.update {
                    it.copy(
                        currentTrackId = trackId,
                        recommendations = filtered,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                // Graceful fallback: fail quietly without affecting playback
                _uiState.update {
                    it.copy(
                        currentTrackId = trackId,
                        recommendations = emptyList(),
                        isLoading = false,
                        errorMessage = err.message
                    )
                }
            }
        }
    }

    /**
     * Trigger explicit manual refresh if desired
     */
    fun refresh() {
        val currentId = playbackManager.playbackState.value.currentTrack?.id
        handleTrackChanged(currentId)
    }

    class Factory(
        private val playbackManager: PlaybackManager,
        private val musicRepository: MusicRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecommendationViewModel(playbackManager, musicRepository) as T
        }
    }
}
