package com.missingcore.music.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.missingcore.music.data.local.LocalAudioScanner
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.repository.FavoritesRepository
import com.missingcore.music.domain.repository.MusicRepository
import com.missingcore.music.domain.repository.PlaylistRepository
import com.missingcore.music.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val isLoading: Boolean = true,
    val isOffline: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val undergroundTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val featuredArtists: List<Artist> = emptyList(),
    val customPlaylists: List<Playlist> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val localAudioScanner: LocalAudioScanner,
    val playbackManager: PlaybackManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPlaylists = playlistRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val trendingResult = musicRepository.getTrending()
                val undergroundResult = musicRepository.getUndergroundTrending()

                val trending = trendingResult.getOrDefault(emptyList())
                val underground = undergroundResult.getOrDefault(emptyList())

                if (trending.isEmpty() && underground.isEmpty()) {
                    // Automatically load local tracks when offline / unable to connect
                    loadOfflineLocalTracks()
                } else {
                    val artists = trending.map { it.artist }.distinctBy { it.id }.take(10)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            trendingTracks = trending,
                            undergroundTracks = underground,
                            featuredArtists = artists,
                            errorMessage = null
                        )
                    }
                }
            } catch (_: Exception) {
                loadOfflineLocalTracks()
            }
        }
    }

    private suspend fun loadOfflineLocalTracks() {
        val local = withContext(Dispatchers.IO) {
            localAudioScanner.scanLocalAudio()
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                isOffline = true,
                localTracks = local,
                errorMessage = null
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isSearching = true) }
            if (_uiState.value.isOffline) {
                val localMatches = _uiState.value.localTracks.filter {
                    it.title.contains(query, ignoreCase = true) || it.artist.name.contains(query, ignoreCase = true)
                }
                _uiState.update { it.copy(isSearching = false, searchResults = localMatches) }
            } else {
                val results = musicRepository.search(query).getOrDefault(emptyList())
                _uiState.update { it.copy(isSearching = false, searchResults = results) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", searchResults = emptyList(), isSearching = false) }
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

    fun playNext(track: Track) {
        playbackManager.playNext(track)
    }

    fun addToQueue(track: Track) {
        playbackManager.addToQueue(track)
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, track)
        }
    }

    class Factory(
        private val musicRepository: MusicRepository,
        private val favoritesRepository: FavoritesRepository,
        private val playlistRepository: PlaylistRepository,
        private val localAudioScanner: LocalAudioScanner,
        private val playbackManager: PlaybackManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                musicRepository,
                favoritesRepository,
                playlistRepository,
                localAudioScanner,
                playbackManager
            ) as T
        }
    }
}
