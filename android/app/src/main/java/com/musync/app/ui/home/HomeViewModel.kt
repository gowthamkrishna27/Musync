package com.musync.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musync.app.core.language.LanguageNormalizer
import com.musync.app.data.local.datastore.PreferencesManager
import com.musync.app.data.local.scanner.LocalAudioScanner
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
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
    val preferredLanguages: Set<String> = setOf("Telugu", "Hindi", "English"),
    val languagePills: List<String> = listOf("All", "Telugu", "Hindi", "English"),
    
    // Personalized Recommendation Sections
    val madeForYouTracks: List<Track> = emptyList(),
    val newForYouTracks: List<Track> = emptyList(),
    val trendingForYouTracks: List<Track> = emptyList(),
    val becauseYouListenToTracks: List<Track> = emptyList(),
    val becauseArtistName: String? = null,

    val teluguTracks: List<Track> = emptyList(),
    val tamilTracks: List<Track> = emptyList(),
    val hindiTracks: List<Track> = emptyList(),
    val globalTracks: List<Track> = emptyList(),
    val trendingTracks: List<Track> = emptyList(),
    val undergroundTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val recentlyPlayedTracks: List<Track> = emptyList(),
    val featuredArtists: List<Artist> = emptyList(),
    val customPlaylists: List<Playlist> = emptyList(),
    val errorMessage: String? = null
)

class HomeViewModel(
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    private val playlistRepository: PlaylistRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val localAudioScanner: LocalAudioScanner,
    val playbackManager: PlaybackManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    val favorites = favoritesRepository.getFavorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userPlaylists = playlistRepository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Stream live recently played tracks
        viewModelScope.launch {
            recentlyPlayedRepository.getRecentlyPlayed(20).collect { recents ->
                _uiState.update { it.copy(recentlyPlayedTracks = recents) }
            }
        }

        // Reactively observe Preferred Music Languages from Settings
        viewModelScope.launch {
            preferencesManager.musicLanguages.collect { langs ->
                val displayNames = LanguageNormalizer.toDisplayNameSet(langs).toList()
                val pills = listOf("All") + displayNames + listOf("Chill", "Party", "Workout", "Focus")
                _uiState.update {
                    it.copy(
                        preferredLanguages = langs,
                        languagePills = pills
                    )
                }
                refreshPersonalizedRecommendations(langs)
            }
        }

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
                val langName = LanguageNormalizer.toDisplayName(language)
                val results = musicRepository.getDiscoverTrending(language = langName).getOrDefault(emptyList())
                if (results.isNotEmpty()) {
                    _uiState.update { current ->
                        when (langName) {
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

    /**
     * Core Recommendation Pipeline:
     * 1. Evaluates user preferred languages (e.g. te, hi, en)
     * 2. Incorporates listening history & session affinities
     * 3. Fetches candidates and ranks them with controlled diversity (40% L1, 30% L2, 20% L3, 10% Discovery)
     */
    fun refreshPersonalizedRecommendations(preferredLanguages: Set<String>) {
        viewModelScope.launch {
            try {
                val langCodes = LanguageNormalizer.toCodeSet(preferredLanguages)
                val sessionProfile = playbackManager.sessionTracker.sessionProfile
                val weights = LanguageNormalizer.computeLanguageWeights(langCodes, sessionProfile.languageAffinities)

                val madeForYouCandidates = mutableListOf<Track>()
                val newReleasesCandidates = mutableListOf<Track>()
                val trendingCandidates = mutableListOf<Track>()

                // Fetch parallel pools per preferred language
                for (code in langCodes) {
                    val langName = LanguageNormalizer.toDisplayName(code)
                    launch {
                        try {
                            val trending = musicRepository.getDiscoverTrending("india", langName).getOrDefault(emptyList())
                            val newDrops = musicRepository.getDiscoverNew(langName).getOrDefault(emptyList())
                            synchronized(trendingCandidates) { trendingCandidates.addAll(trending) }
                            synchronized(newReleasesCandidates) { newReleasesCandidates.addAll(newDrops) }
                            synchronized(madeForYouCandidates) {
                                madeForYouCandidates.addAll(trending.take(6))
                                madeForYouCandidates.addAll(newDrops.take(6))
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Add discovery candidates
                val globalNew = musicRepository.getDiscoverNew("All").getOrDefault(emptyList())
                val globalTrending = musicRepository.getDiscoverTrending("global", "All").getOrDefault(emptyList())
                madeForYouCandidates.addAll(globalNew.take(4))
                madeForYouCandidates.addAll(globalTrending.take(4))

                // Score and rank candidates using language affinity + artist affinity + completion history
                val scoredMadeForYou = interleaveAndRank(madeForYouCandidates, langCodes, sessionProfile)
                val scoredNewForYou = interleaveAndRank(newReleasesCandidates.ifEmpty { globalNew }, langCodes, sessionProfile)
                val scoredTrendingForYou = interleaveAndRank(trendingCandidates.ifEmpty { globalTrending }, langCodes, sessionProfile)

                // Compute "Because You Listen To" based on top recent artist
                val recents = _uiState.value.recentlyPlayedTracks
                val topArtist = recents.firstOrNull()?.artist?.name ?: _uiState.value.trendingTracks.firstOrNull()?.artist?.name
                val becauseTracks = if (!topArtist.isNullOrBlank()) {
                    musicRepository.search("artist:$topArtist hits").getOrDefault(emptyList()).take(8)
                } else emptyList()

                _uiState.update {
                    it.copy(
                        madeForYouTracks = scoredMadeForYou,
                        newForYouTracks = scoredNewForYou,
                        trendingForYouTracks = scoredTrendingForYou,
                        becauseYouListenToTracks = becauseTracks,
                        becauseArtistName = topArtist
                    )
                }
            } catch (_: Exception) {}
        }
    }

    private fun interleaveAndRank(
        candidates: List<Track>,
        preferredCodes: Set<String>,
        sessionProfile: com.musync.app.playback.recommendation.SessionProfile
    ): List<Track> {
        val deduped = candidates.distinctBy { it.id }
        if (deduped.isEmpty()) return emptyList()

        // Group by language
        val byLang = mutableMapOf<String, MutableList<Track>>()
        for (track in deduped) {
            val code = LanguageNormalizer.inferLanguage(track)
            byLang.getOrPut(code) { mutableListOf() }.add(track)
        }

        // Interleave to avoid mono-language clusters
        val result = mutableListOf<Track>()
        var addedInRound = true
        val activeCodes = preferredCodes.toList().ifEmpty { listOf("te", "hi", "en") }

        while (addedInRound && result.size < 24) {
            addedInRound = false
            for (code in activeCodes) {
                val list = byLang[code]
                if (!list.isNullOrEmpty()) {
                    result.add(list.removeAt(0))
                    addedInRound = true
                }
            }
            // Controlled discovery item
            val otherLangs = byLang.keys.filter { it !in activeCodes }
            for (other in otherLangs) {
                val list = byLang[other]
                if (!list.isNullOrEmpty()) {
                    result.add(list.removeAt(0))
                    addedInRound = true
                    break
                }
            }
        }

        return if (result.isNotEmpty()) result else deduped.take(20)
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

                // Refresh personalization using preferred languages
                val preferred = preferencesManager.getMusicLanguages()
                refreshPersonalizedRecommendations(preferred)

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
        private val recentlyPlayedRepository: RecentlyPlayedRepository,
        private val localAudioScanner: LocalAudioScanner,
        private val playbackManager: PlaybackManager,
        private val preferencesManager: PreferencesManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(
                musicRepository,
                favoritesRepository,
                playlistRepository,
                recentlyPlayedRepository,
                localAudioScanner,
                playbackManager,
                preferencesManager
            ) as T
        }
    }
}


