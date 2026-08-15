package com.missingcore.music.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.missingcore.music.domain.model.PlaybackState
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.repository.FavoritesRepository
import com.missingcore.music.playback.PlaybackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlayerViewModel(
    val playbackManager: PlaybackManager,
    private val favoritesRepository: FavoritesRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState

    val isCurrentTrackFavorite: StateFlow<Boolean> = playbackManager.playbackState
        .map { state ->
            val trackId = state.currentTrack?.id ?: return@map false
            favoritesRepository.isFavorite(trackId)
        }
        .map { false } // fallback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePlay() {
        playbackManager.togglePlayPause()
    }

    fun skipNext() {
        playbackManager.skipNext()
    }

    fun skipPrevious() {
        playbackManager.skipPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleFavorite(track: Track?) {
        if (track == null) return
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(track)
        }
    }

    fun toggleShuffle() {
        val current = playbackState.value.isShuffle
        playbackManager.setShuffle(!current)
    }

    fun toggleRepeat() {
        playbackManager.toggleRepeatMode()
    }

    fun playTrackAtIndex(index: Int) {
        playbackManager.playAtIndex(index)
    }

    fun removeFromQueue(trackId: String) {
        playbackManager.removeFromQueue(trackId)
    }

    fun clearQueue() {
        playbackManager.clearQueue()
    }

    class Factory(
        private val playbackManager: PlaybackManager,
        private val favoritesRepository: FavoritesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(playbackManager, favoritesRepository) as T
        }
    }
}
