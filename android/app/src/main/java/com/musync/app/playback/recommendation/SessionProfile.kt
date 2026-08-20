package com.musync.app.playback.recommendation

import java.util.ArrayDeque

/**
 * Real-time in-memory session profile tracking genre/artist affinities,
 * skip learning, and recency cooldowns for the current listening session.
 *
 * This is a lightweight, on-device signal aggregator. Signals are also
 * asynchronously synced to the backend via ListeningSessionTracker.
 *
 * The scoring functions are designed so the ML model can be dropped in
 * as a replacement for the weighted heuristics in IntelligentShuffleEngine.
 */
data class SessionProfile(
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val sessionStartMs: Long = System.currentTimeMillis(),

    // Affinity scores per genre/artist [0.0, 1.0]. Default 0.5 = neutral.
    val genreAffinities: MutableMap<String, Float> = mutableMapOf(),
    val artistAffinities: MutableMap<String, Float> = mutableMapOf(),

    // Skip learning: track-level, artist-level, genre-level skip counts
    val skipCounts: MutableMap<String, Int> = mutableMapOf(),         // trackId -> skip count
    val completionCounts: MutableMap<String, Int> = mutableMapOf(),   // trackId -> completion count
    val artistSkipCounts: MutableMap<String, Int> = mutableMapOf(),   // artistName -> skip count
    val genreSkipCounts: MutableMap<String, Int> = mutableMapOf(),    // genre -> skip count

    // Rolling recency deque: last 50 played trackIds (index 0 = most recent)
    private val _recentTrackIds: ArrayDeque<String> = ArrayDeque(50),
    private val _recentArtistNames: ArrayDeque<String> = ArrayDeque(20),

    var totalEvents: Int = 0
) {
    companion object {
        private const val RECENCY_CAPACITY = 50
        private const val ARTIST_RECENCY_CAPACITY = 20
        private const val AFFINITY_DECAY = 0.9f     // exponential recency decay
    }

    val recentTrackIds: List<String> get() = _recentTrackIds.toList()
    val recentArtistNames: List<String> get() = _recentArtistNames.toList()

    // -----------------------------------------------------------------------
    // Event handlers — called by ListeningSessionTracker
    // -----------------------------------------------------------------------

    fun onTrackStarted(trackId: String, artistName: String) {
        totalEvents++
        _recentTrackIds.remove(trackId)
        _recentTrackIds.addFirst(trackId)
        if (_recentTrackIds.size > RECENCY_CAPACITY) _recentTrackIds.removeLast()

        val normArtist = artistName.lowercase().trim()
        _recentArtistNames.remove(normArtist)
        _recentArtistNames.addFirst(normArtist)
        if (_recentArtistNames.size > ARTIST_RECENCY_CAPACITY) _recentArtistNames.removeLast()
    }

    fun onTrackCompleted(trackId: String, artistName: String, genre: String?) {
        totalEvents++
        val boost = 0.3f
        adjustArtistAffinity(artistName, boost)
        genre?.let { adjustGenreAffinity(it, boost) }
        completionCounts[trackId] = (completionCounts[trackId] ?: 0) + 1
    }

    fun onTrackReplayed(trackId: String, artistName: String, genre: String?) {
        totalEvents++
        val boost = 0.4f
        adjustArtistAffinity(artistName, boost)
        genre?.let { adjustGenreAffinity(it, boost) }
        completionCounts[trackId] = (completionCounts[trackId] ?: 0) + 1
    }

    fun onTrackSkipped(trackId: String, artistName: String, genre: String?) {
        totalEvents++
        val penalty = 0.2f
        skipCounts[trackId] = (skipCounts[trackId] ?: 0) + 1

        val normArtist = artistName.lowercase().trim()
        val curArtistAffinity = artistAffinities[normArtist] ?: 0.5f
        artistAffinities[normArtist] = (curArtistAffinity - penalty).coerceAtLeast(0f)
        artistSkipCounts[normArtist] = (artistSkipCounts[normArtist] ?: 0) + 1

        genre?.let {
            val normGenre = it.lowercase().trim()
            val curGenreAffinity = genreAffinities[normGenre] ?: 0.5f
            genreAffinities[normGenre] = (curGenreAffinity - penalty).coerceAtLeast(0f)
            genreSkipCounts[normGenre] = (genreSkipCounts[normGenre] ?: 0) + 1
        }
    }

    fun onTrackLiked(artistName: String, genre: String?) {
        totalEvents++
        val boost = 0.5f
        adjustArtistAffinity(artistName, boost)
        genre?.let { adjustGenreAffinity(it, boost) }
    }

    fun onTrackUnliked(artistName: String, genre: String?) {
        totalEvents++
        val penalty = 0.3f
        adjustArtistAffinity(artistName, -penalty)
        genre?.let { adjustGenreAffinity(it, -penalty) }
    }

    fun on75Percent(artistName: String, genre: String?) {
        totalEvents++
        val boost = 0.15f
        adjustArtistAffinity(artistName, boost)
        genre?.let { adjustGenreAffinity(it, boost) }
    }

    // -----------------------------------------------------------------------
    // Scoring helpers — used by IntelligentShuffleEngine
    // -----------------------------------------------------------------------

    /** Returns artist affinity [0.0, 1.0]. 0.5 = neutral. */
    fun getArtistAffinity(artistName: String): Float {
        val key = artistName.lowercase().trim()
        return artistAffinities[key] ?: 0.5f
    }

    /** Returns genre affinity [0.0, 1.0]. 0.5 = neutral. */
    fun getGenreAffinity(genre: String): Float {
        val key = genre.lowercase().trim()
        return genreAffinities[key] ?: 0.5f
    }

    /**
     * Skip penalty for an artist. 0 = no penalty, >0 = suppress.
     * Max 0.35 (diminishing returns).
     */
    fun getArtistSkipPenalty(artistName: String): Float {
        val key = artistName.lowercase().trim()
        val skips = artistSkipCounts[key] ?: 0
        return (skips * 0.12f).coerceAtMost(0.35f)
    }

    /**
     * Cooldown penalty for recently played tracks.
     * Tracks in recent positions receive heavy penalties to avoid repetition.
     */
    fun getCooldownPenalty(trackId: String): Float {
        val pos = _recentTrackIds.indexOf(trackId)
        return when {
            pos < 0 -> 0f
            pos < 5 -> 0.8f   // Very recently played — hard suppress
            pos < 15 -> 0.4f
            pos < 30 -> 0.15f
            else -> 0f
        }
    }

    /**
     * How many songs ago this artist last appeared in recentArtistNames.
     * Returns -1 if not found.
     */
    fun artistLastSeenSlot(artistName: String): Int {
        val key = artistName.lowercase().trim()
        return _recentArtistNames.indexOf(key)
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun adjustArtistAffinity(artistName: String, delta: Float) {
        val key = artistName.lowercase().trim()
        if (key.isBlank()) return
        val current = artistAffinities[key] ?: 0.5f
        artistAffinities[key] = (current * AFFINITY_DECAY + delta).coerceIn(0f, 1f)
    }

    private fun adjustGenreAffinity(genre: String, delta: Float) {
        val key = genre.lowercase().trim()
        if (key.isBlank()) return
        val current = genreAffinities[key] ?: 0.5f
        genreAffinities[key] = (current * AFFINITY_DECAY + delta).coerceIn(0f, 1f)
    }
}
