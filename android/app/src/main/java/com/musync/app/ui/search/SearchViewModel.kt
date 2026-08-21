package com.musync.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.playback.PlaybackManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.musync.app.data.local.scanner.LocalAudioScanner
import com.musync.app.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.first

enum class SearchFilter {
    ALL, TRACKS, ARTISTS, PLAYLISTS
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.ALL,
    val isSearching: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val errorMessage: String? = null
)

class SearchViewModel(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()

        if (newQuery.isBlank()) {
            _uiState.update { it.copy(isSearching = false, tracks = emptyList(), artists = emptyList(), playlists = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(150) // Ultra fast snappy 150ms debounce
            performSearch(newQuery, _uiState.value.filter)
        }
    }

    fun onFilterChange(filter: SearchFilter) {
        _uiState.update { it.copy(filter = filter) }
        val query = _uiState.value.query
        if (query.isNotBlank()) {
            searchJob?.cancel()
            searchJob = viewModelScope.launch {
                performSearch(query, filter)
            }
        }
    }

    private suspend fun performSearch(query: String, filter: SearchFilter) = kotlinx.coroutines.coroutineScope {
        _uiState.update { it.copy(isSearching = true, errorMessage = null) }
        try {
            when (filter) {
                SearchFilter.ALL -> {
                    val tracksDef = async { musicRepository.search(query).getOrDefault(emptyList()) }
                    val artistsDef = async { musicRepository.searchArtists(query).getOrDefault(emptyList()) }
                    val playlistsDef = async { musicRepository.searchPlaylists(query).getOrDefault(emptyList()) }

                    val tracks = tracksDef.await()
                    val artists = artistsDef.await()
                    val playlists = playlistsDef.await()

                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            tracks = tracks,
                            artists = artists,
                            playlists = playlists
                        )
                    }
                }
                SearchFilter.TRACKS -> {
                    val tracks = musicRepository.search(query).getOrDefault(emptyList())
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            tracks = tracks
                        )
                    }
                }
                SearchFilter.ARTISTS -> {
                    val artists = musicRepository.searchArtists(query).getOrDefault(emptyList())
                    _uiState.update { it.copy(isSearching = false, artists = artists) }
                }
                SearchFilter.PLAYLISTS -> {
                    val playlists = musicRepository.searchPlaylists(query).getOrDefault(emptyList())
                    _uiState.update { it.copy(isSearching = false, playlists = playlists) }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    tracks = emptyList(),
                    errorMessage = e.message
                )
            }
        }
    }

    fun playTrack(track: Track) {
        val tracks = _uiState.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playbackManager.playTracks(tracks, index)
        } else {
            playbackManager.play(track)
        }
    }

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            favoritesRepository.toggleFavorite(track)
        }
    }

    class Factory(
        private val musicRepository: MusicRepository,
        private val favoritesRepository: FavoritesRepository,
        private val playbackManager: PlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(
                musicRepository,
                favoritesRepository,
                playbackManager
            ) as T
        }
    }
}
