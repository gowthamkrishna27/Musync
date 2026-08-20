package com.musync.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.data.local.scanner.LocalAudioScanner
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.playback.PlaybackManager
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
    val selectedLanguage: String = "All",
    val teluguTracks: List<Track> = emptyList(),
    val tamilTracks: List<Track> = emptyList(),
    val hindiTracks: List<Track> = emptyList(),
    val globalTracks: List<Track> = emptyList(),
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

    fun selectLanguage(language: String) {
        _uiState.update { it.copy(selectedLanguage = language) }
        if (language != "All") {
            loadSpecificLanguageTracks(language)
        }
    }

    private fun loadSpecificLanguageTracks(language: String) {
        viewModelScope.launch {
            try {
                val results = musicRepository.getDiscoverTrending(language = language).getOrDefault(emptyList())
                if (results.isNotEmpty()) {
                    _uiState.update { current ->
                        when (language) {
                            "Telugu" -> current.copy(teluguTracks = results)
                            "Tamil" -> current.copy(tamilTracks = results)
                            "Hindi" -> current.copy(hindiTracks = results)
                            else -> current.copy(globalTracks = results)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Fetch live trending songs
                val trendingResult = musicRepository.getDiscoverTrending("global", "All")
                val trending = trendingResult.getOrDefault(emptyList())

                if (trending.isEmpty()) {
                    val fallback = musicRepository.getTrending().getOrDefault(emptyList())
                    if (fallback.isEmpty()) {
                        loadOfflineLocalTracks()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            trendingTracks = fallback,
                            teluguTracks = fallback,
                            errorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isOffline = false,
                            trendingTracks = trending,
                            teluguTracks = trending,
                            errorMessage = null
                        )
                    }
                }

                // Parallel live discovery fetch for regional catalogs
                launch {
                    val telugu = musicRepository.getDiscoverTrending("india", "Telugu").getOrDefault(emptyList())
                    if (telugu.isNotEmpty()) {
                        _uiState.update { it.copy(teluguTracks = telugu) }
                    }
                }
                launch {
                    val tamil = musicRepository.getDiscoverTrending("india", "Tamil").getOrDefault(emptyList())
                    if (tamil.isNotEmpty()) {
                        _uiState.update { it.copy(tamilTracks = tamil) }
                    }
                }
                launch {
                    val hindi = musicRepository.getDiscoverTrending("india", "Hindi").getOrDefault(emptyList())
                    if (hindi.isNotEmpty()) {
                        _uiState.update { it.copy(hindiTracks = hindi) }
                    }
                }
                launch {
                    val global = musicRepository.getDiscoverNew("All").getOrDefault(emptyList())
                    if (global.isNotEmpty()) {
                        _uiState.update { it.copy(globalTracks = global) }
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

