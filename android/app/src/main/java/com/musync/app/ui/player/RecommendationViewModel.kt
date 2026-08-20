package com.musync.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.PlaybackManager
import com.musync.app.playback.recommendation.RealTimeRecommendationEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecommendedTrackWithReason(
    val track: Track,
    val reason: String? = null    // e.g. "Because you like Kendrick Lamar"
)

data class RecommendationUiState(
    val currentTrackId: String? = null,
    val recommendations: List<Track> = emptyList(),
    val recommendationsWithReasons: List<RecommendedTrackWithReason> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Recommendation ViewModel that uses the [RealTimeRecommendationEngine] for
 * session-aware, personalised next-track suggestions.
 *
 * Falls back to [MusicRepository.getRecommendations] if the engine is unavailable.
 */
class RecommendationViewModel(
    private val playbackManager: PlaybackManager,
    private val musicRepository: MusicRepository,
    private val recommendationEngine: RealTimeRecommendationEngine? = null
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
        recommendationJob?.cancel()

        if (trackId.isNullOrBlank()) {
            _uiState.value = RecommendationUiState(currentTrackId = null, recommendations = emptyList())
            return
        }

        recommendationJob = viewModelScope.launch {
            _uiState.update { it.copy(currentTrackId = trackId, isLoading = true, errorMessage = null) }

            // Debounce rapid track skipping
            delay(250)

            val currentTrack = playbackManager.playbackState.value.currentTrack

            if (recommendationEngine != null && currentTrack != null) {
                // Use real-time session-aware engine
                try {
                    val tracks = recommendationEngine.getNextRecommendations(currentTrack, limit = 6)
                    val filtered = tracks.filter {
                        it.id != trackId && it.id.removePrefix("yt_") != trackId.removePrefix("yt_")
                    }
                    val withReasons = filtered.map { track ->
                        val session = recommendationEngine.sessionProfile
                        val artistAffinity = session.getArtistAffinity(track.artist.name)
                        val reason = when {
                            artistAffinity > 0.75f -> "Because you like ${track.artist.name}"
                            session.getGenreAffinity(track.genre ?: "") > 0.75f -> "Based on your taste"
                            session.completionCounts[track.id] != null -> "You've enjoyed this before"
                            else -> null
                        }
                        RecommendedTrackWithReason(track, reason)
                    }
                    _uiState.update {
                        it.copy(
                            currentTrackId = trackId,
                            recommendations = filtered,
                            recommendationsWithReasons = withReasons,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                    return@launch
                } catch (e: Exception) {
                    // Fall through to repository fallback
                }
            }

            // Fallback: existing MusicRepository recommendations
            val result = musicRepository.getRecommendations(trackId, limit = 6)
            result.onSuccess { tracks ->
                val filtered = tracks.filter {
                    it.id != trackId && it.id.removePrefix("yt_") != trackId.removePrefix("yt_")
                }
                _uiState.update {
                    it.copy(
                        currentTrackId = trackId,
                        recommendations = filtered,
                        recommendationsWithReasons = filtered.map { t -> RecommendedTrackWithReason(t) },
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        currentTrackId = trackId,
                        recommendations = emptyList(),
                        recommendationsWithReasons = emptyList(),
                        isLoading = false,
                        errorMessage = err.message
                    )
                }
            }
        }
    }

    fun refresh() {
        val currentId = playbackManager.playbackState.value.currentTrack?.id
        handleTrackChanged(currentId)
    }

    class Factory(
        private val playbackManager: PlaybackManager,
        private val musicRepository: MusicRepository,
        private val recommendationEngine: RealTimeRecommendationEngine? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecommendationViewModel(playbackManager, musicRepository, recommendationEngine) as T
        }
    }
}
