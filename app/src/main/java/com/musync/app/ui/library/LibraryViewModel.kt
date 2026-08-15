package com.musync.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.data.local.LocalAudioScanner
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
import com.musync.app.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab {
    FAVORITES, PLAYLISTS, RECENT
}

class LibraryViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val localAudioScanner: LocalAudioScanner,
    val playbackManager: PlaybackManager
) : ViewModel() {

    val favorites: StateFlow<List<Track>> = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<Track>> = recentlyPlayedRepository.getRecentlyPlayed(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _localTracks = MutableStateFlow<List<Track>>(emptyList())
    val localTracks: StateFlow<List<Track>> = _localTracks.asStateFlow()

    private val _isScanningLocal = MutableStateFlow(false)
    val isScanningLocal: StateFlow<Boolean> = _isScanningLocal.asStateFlow()

    init {
        loadLocalTracks()
    }

    fun loadLocalTracks() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanningLocal.value = true
            try {
                val tracks = localAudioScanner.scanLocalAudio()
                _localTracks.value = tracks
            } catch (_: Exception) {
            } finally {
                _isScanningLocal.value = false
            }
        }
    }

    fun playTrack(track: Track, fromList: List<Track>) {
        val index = fromList.indexOfFirst { it.id == track.id }
        playbackManager.playTracks(fromList, if (index >= 0) index else 0)
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(track)
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                playlistRepository.createPlaylist(name, description)
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun clearRecentlyPlayed() {
        viewModelScope.launch {
            recentlyPlayedRepository.clearHistory()
        }
    }

    class Factory(
        private val favoritesRepository: FavoritesRepository,
        private val playlistRepository: PlaylistRepository,
        private val recentlyPlayedRepository: RecentlyPlayedRepository,
        private val localAudioScanner: LocalAudioScanner,
        private val playbackManager: PlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LibraryViewModel(
                favoritesRepository,
                playlistRepository,
                recentlyPlayedRepository,
                localAudioScanner,
                playbackManager
            ) as T
        }
    }
}

