package com.musync.app.playback.recommendation

import android.util.Log
import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time recommendation engine that:
 *
 *  1. Generates candidates from multiple local + remote sources
 *  2. Ranks candidates using IntelligentShuffleEngine's scoring with the real-time SessionProfile
 *  3. Pre-generates next recommendations at 75% track completion
 *  4. Maintains a rolling queue of pre-fetched suggestions (avoids gaps)
 *
 * This engine does NOT modify ExoPlayer's queue directly — it exposes
 * [getNextRecommendations] and relies on PlaybackManager to enqueue tracks.
 */
class RealTimeRecommendationEngine(
    private val scope: CoroutineScope,
    private val musicRepository: MusicRepository,
    private val favoritesRepository: FavoritesRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val sessionTracker: ListeningSessionTracker,
    private val preferencesManager: com.musync.app.data.local.datastore.PreferencesManager? = null
) {
    companion object {
        private const val TAG = "RealTimeRecEngine"
        private const val PREGENERATED_POOL_SIZE = 8   // Keep 8 songs pre-ranked in the pool
        private const val MIN_POOL_THRESHOLD = 3        // Re-fetch when pool drops below 3
    }

    val sessionProfile: SessionProfile get() = sessionTracker.sessionProfile
    val preferredLanguages: Set<String>
        get() = preferencesManager?.getMusicLanguages() ?: setOf("Telugu", "Hindi", "English")

    // Rolling pre-generated recommendation pool
    private val _recommendationPool = mutableListOf<Track>()
    private var _currentGeneratingTrackId: String? = null
    private var _pregenerationJob: Job? = null
    private val _isGenerating = AtomicBoolean(false)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val shuffleEngine: IntelligentShuffleEngine
        get() = IntelligentShuffleEngine(sessionProfile, preferredLanguages)

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Returns up to [limit] recommended tracks for [currentTrack].
     * Draws from the pre-generated pool if available, otherwise fetches synchronously.
     *
     * This method is intended for immediate consumption (e.g., populating QueueSheet).
     */
    suspend fun getNextRecommendations(currentTrack: Track, limit: Int = 6): List<Track> {
        return withContext(Dispatchers.IO) {
            // If pool has entries, return from it
            if (_recommendationPool.size >= limit) {
                Log.d(TAG, "Serving ${limit} from pre-generated pool (pool size: ${_recommendationPool.size})")
                return@withContext _recommendationPool.take(limit)
            }
            // Otherwise generate fresh
            generateRecommendations(currentTrack, limit)
        }
    }

    /**
     * Call this at ~75% of track completion to pre-generate recommendations
     * for the next song without blocking playback.
     */
    fun triggerPreGeneration(currentTrack: Track) {
        if (currentTrack.id == _currentGeneratingTrackId && _isGenerating.get()) return
        if (_recommendationPool.size >= PREGENERATED_POOL_SIZE) return

        _currentGeneratingTrackId = currentTrack.id
        _pregenerationJob?.cancel()
        _pregenerationJob = scope.launch(Dispatchers.IO) {
            if (_isGenerating.compareAndSet(false, true)) {
                try {
                    Log.d(TAG, "Pre-generating recommendations for '${currentTrack.title}'")
                    val fresh = generateRecommendations(currentTrack, PREGENERATED_POOL_SIZE)
                    val existingIds = _recommendationPool.map { it.id }.toSet()
                    val newEntries = fresh.filter { it.id !in existingIds }
                    _recommendationPool.addAll(newEntries)
                    Log.d(TAG, "Pool updated: ${_recommendationPool.size} pre-generated songs")
                } finally {
                    _isGenerating.set(false)
                }
            }
        }
    }

    /**
     * Consume and remove [count] tracks from the pre-generated pool.
     * Returns empty list if pool is empty.
     */
    fun consumeFromPool(count: Int = 1): List<Track> {
        val taken = _recommendationPool.take(count)
        repeat(taken.size) { if (_recommendationPool.isNotEmpty()) _recommendationPool.removeAt(0) }
        return taken
    }

    /**
     * Clear the pool (e.g., when user manually changes queue context).
     */
    fun clearPool() {
        _recommendationPool.clear()
        _currentGeneratingTrackId = null
        _pregenerationJob?.cancel()
    }

    /**
     * Apply intelligent shuffle ordering to an existing list of tracks.
     * Uses softmax-weighted probabilistic selection with session-aware scoring.
     */
    fun intelligentShuffle(tracks: List<Track>, currentTrack: Track?): List<Track> {
        return shuffleEngine.shuffle(tracks, currentTrack)
    }

    // -----------------------------------------------------------------------
    // Core generation logic
    // -----------------------------------------------------------------------

    private suspend fun generateRecommendations(currentTrack: Track, limit: Int): List<Track> {
        val candidates = mutableListOf<Track>()
        val currentTrackId = currentTrack.id

        // Source 1: Backend /api/recommendations/next (primary source with session integration)
        try {
            val backendRecs = fetchFromBackend(currentTrackId, limit + 4)
            candidates.addAll(backendRecs)
            Log.d(TAG, "Source 1 (backend): ${backendRecs.size} candidates")
        } catch (e: Exception) {
            Log.w(TAG, "Backend source failed: ${e.message}")
        }

        // Source 2: Recently played tracks that are likely interesting again
        try {
            val recents = recentlyPlayedRepository.getRecentlyPlayed(20).first()
            val recentFiltered = recents
                .filter { it.id != currentTrackId }
                .filter { sessionProfile.getCooldownPenalty(it.id) < 0.4f }
                .take(5)
            candidates.addAll(recentFiltered)
            Log.d(TAG, "Source 2 (recently played): ${recentFiltered.size} candidates")
        } catch (e: Exception) {
            Log.w(TAG, "Recently played source failed: ${e.message}")
        }

        // Source 3: Favorites with high affinity for current artist/genre
        try {
            val favorites = favoritesRepository.getFavorites().first()
            val favFiltered = favorites
                .filter { it.id != currentTrackId }
                .filter {
                    sessionProfile.getArtistAffinity(it.artist.name) > 0.6f ||
                    sessionProfile.getGenreAffinity(it.genre ?: "") > 0.6f
                }
                .take(4)
            candidates.addAll(favFiltered)
            Log.d(TAG, "Source 3 (favorites): ${favFiltered.size} candidates")
        } catch (e: Exception) {
            Log.w(TAG, "Favorites source failed: ${e.message}")
        }

        // Source 4: Repository recommendations (existing logic)
        if (candidates.size < limit) {
            try {
                val repoRecs = musicRepository.getRecommendations(currentTrackId, limit).getOrNull() ?: emptyList()
                candidates.addAll(repoRecs)
                Log.d(TAG, "Source 4 (repository): ${repoRecs.size} candidates")
            } catch (e: Exception) {
                Log.w(TAG, "Repository source failed: ${e.message}")
            }
        }

        // Deduplicate by track ID
        val deduped = candidates
            .distinctBy { it.id }
            .filter { it.id != currentTrackId }

        if (deduped.isEmpty()) return emptyList()

        // Rank using IntelligentShuffleEngine with current session profile
        // The shuffle engine uses session-aware scoring to pick the best ordering
        val ranked = shuffleEngine.shuffle(deduped, currentTrack)
        return ranked.take(limit)
    }

    private suspend fun fetchFromBackend(trackId: String, limit: Int): List<Track> {
        val baseUrl = sessionTracker.backendBaseUrl
        if (baseUrl.isBlank()) return emptyList()

        val cleanId = trackId.removePrefix("yt_")
        val userId = sessionTracker.userId
        val url = "$baseUrl/api/recommendations/next?trackId=$cleanId&limit=$limit&userId=$userId"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Musync-Android/1.0")
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                parseRecommendationsResponse(body, baseUrl)
            }
        }
    }

    private fun parseRecommendationsResponse(json: String, baseUrl: String): List<Track> {
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("recommendations") ?: return emptyList()
            val tracks = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val videoId = obj.optString("videoId")
                if (videoId.isBlank()) continue
                val title = obj.optString("title", "Unknown Title")
                val artistName = obj.optString("artist", "YouTube Artist")
                val artworkUrl = obj.optString("image_url").ifBlank {
                    "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                }
                val durationSec = obj.optLong("duration_seconds", 180L)
                val streamUrl = obj.optString("stream_url").ifBlank {
                    "$baseUrl/stream?id=$videoId"
                }
                val artistObj = Artist(id = "yt_artist_${artistName.hashCode()}", name = artistName)
                val albumObj = Album(
                    id = "yt_album_${videoId.hashCode()}",
                    name = title,
                    artist = artistObj,
                    artworkUrl = artworkUrl
                )
                tracks.add(
                    Track(
                        id = "yt_$videoId",
                        title = title,
                        artist = artistObj,
                        album = albumObj,
                        durationMs = durationSec * 1000L,
                        streamUrl = streamUrl,
                        artworkUrl = artworkUrl,
                        genre = "Music"
                    )
                )
            }
            tracks
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse recommendations response: ${e.message}")
            emptyList()
        }
    }
}
