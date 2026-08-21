package com.musync.app.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.playback.PlaybackManager
import com.musync.app.playback.recommendation.RealTimeRecommendationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaylistViewModel(
    private val playlistId: String,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
    val playbackManager: PlaybackManager,
    private val musicRepository: MusicRepository? = null,
    private val recommendationEngine: RealTimeRecommendationEngine? = null
) : ViewModel() {

    val playlist: StateFlow<Playlist?> = playlistRepository.getPlaylistWithTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _recommendations = MutableStateFlow<List<Track>>(emptyList())
    val recommendations: StateFlow<List<Track>> = _recommendations.asStateFlow()

    private val _isLoadingRecommendations = MutableStateFlow(false)
    val isLoadingRecommendations: StateFlow<Boolean> = _isLoadingRecommendations.asStateFlow()

    private var recommendationJob: Job? = null

    init {
        observePlaylistForRecommendations()
    }

    private fun observePlaylistForRecommendations() {
        viewModelScope.launch {
            playlist.collect { pl ->
                if (pl != null) {
                    loadRecommendationsForPlaylist(pl)
                }
            }
        }
    }

    fun refreshRecommendations() {
        playlist.value?.let { loadRecommendationsForPlaylist(it) }
    }

    private fun loadRecommendationsForPlaylist(pl: Playlist) {
        recommendationJob?.cancel()
        recommendationJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoadingRecommendations.value = true
            val existingIds = pl.tracks.map { it.id }.toSet()
            val candidateRecs = mutableListOf<Track>()

            try {
                if (pl.tracks.isNotEmpty()) {
                    // Seed from up to 3 diverse tracks in the playlist
                    val seedTracks = pl.tracks.shuffled().take(3)
                    for (seed in seedTracks) {
                        if (recommendationEngine != null) {
                            val recs = recommendationEngine.getNextRecommendations(seed, limit = 5)
                            candidateRecs.addAll(recs)
                        } else if (musicRepository != null) {
                            val recs = musicRepository.getRecommendations(seed.id, limit = 5).getOrNull() ?: emptyList()
                            candidateRecs.addAll(recs)
                        }
                    }
                }

                // If scarce or empty playlist, fetch trending songs
                if (candidateRecs.size < 6 && musicRepository != null) {
                    val trending = musicRepository.getTrending().getOrNull() ?: emptyList()
                    candidateRecs.addAll(trending)
                }

                val filtered = candidateRecs
                    .distinctBy { it.id }
                    .filter { it.id !in existingIds }
                    .take(8)

                _recommendations.value = filtered
            } catch (e: Exception) {
                android.util.Log.w("PlaylistViewModel", "Failed to load playlist recommendations: ${e.message}")
            } finally {
                _isLoadingRecommendations.value = false
            }
        }
    }

    fun playTrack(track: Track, fromList: List<Track>) {
        val index = fromList.indexOfFirst { it.id == track.id }
        playbackManager.playTracks(fromList, if (index >= 0) index else 0)
    }

    fun playAll() {
        val tracks = playlist.value?.tracks ?: return
        if (tracks.isNotEmpty()) {
            playbackManager.playTracks(tracks, 0)
        }
    }

    fun playIntelligentShuffle() {
        val tracks = playlist.value?.tracks ?: return
        if (tracks.isEmpty()) return

        if (recommendationEngine != null) {
            val current = playbackManager.playbackState.value.currentTrack
            val shuffled = recommendationEngine.intelligentShuffle(tracks, current)
            playbackManager.playTracks(shuffled, 0)
        } else {
            playbackManager.playTracks(tracks.shuffled(), 0)
        }
    }

    fun addTrackToPlaylist(track: Track) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, track)
            // Remove from current recommendations list immediately for responsive UI
            _recommendations.update { list -> list.filter { it.id != track.id } }
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(track)
        }
    }

    class Factory(
        private val playlistId: String,
        private val playlistRepository: PlaylistRepository,
        private val favoritesRepository: FavoritesRepository,
        private val playbackManager: PlaybackManager,
        private val musicRepository: MusicRepository? = null,
        private val recommendationEngine: RealTimeRecommendationEngine? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistViewModel(
                playlistId,
                playlistRepository,
                favoritesRepository,
                playbackManager,
                musicRepository,
                recommendationEngine
            ) as T
        }
    }
}

