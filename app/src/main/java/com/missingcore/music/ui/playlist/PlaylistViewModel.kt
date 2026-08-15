package com.missingcore.music.ui.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.repository.FavoritesRepository
import com.missingcore.music.domain.repository.PlaylistRepository
import com.missingcore.music.playback.PlaybackManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistViewModel(
    private val playlistId: String,
    private val playlistRepository: PlaylistRepository,
    private val favoritesRepository: FavoritesRepository,
    val playbackManager: PlaybackManager
) : ViewModel() {

    val playlist: StateFlow<Playlist?> = playlistRepository.getPlaylistWithTracks(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
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
        private val playbackManager: PlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistViewModel(playlistId, playlistRepository, favoritesRepository, playbackManager) as T
        }
    }
}
