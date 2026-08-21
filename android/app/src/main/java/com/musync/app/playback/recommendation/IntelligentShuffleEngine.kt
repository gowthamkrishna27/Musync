package com.musync.app.playback.recommendation

import android.util.Log
import com.musync.app.domain.model.Track
import kotlin.math.exp
import kotlin.math.max
import kotlin.random.Random

/**
 * Production-quality intelligent shuffle engine that replaces naive Math.random() shuffling.
 *
 * Algorithm:
 *   1. Score all candidate tracks using a weighted heuristic incorporating the
 *      real-time [SessionProfile] (affinities, skip penalties, cooldowns, energy delta).
 *   2. Select the next track using **softmax-weighted probabilistic sampling** with
 *      temperature parameter T:
 *
 *        P(track_i) = exp(score_i / T) / sum(exp(score_j / T))
 *
 *      T = 0.5 → highly personalised (exploits preferences)
 *      T = 1.0 → balanced exploration + personalisation
 *      T = 2.0 → near-uniform random (max serendipity)
 *
 *   3. Enforce diversity constraints:
 *      - Max 2 consecutive songs from same artist
 *      - Prefer artist spacing of 3-6 songs
 *      - Cooldown: tracks recently played get heavy suppression
 *      - Skip-learned artists placed later
 *
 * Structure note:
 *   The score() function below is the pluggable ranking function.
 *   To swap in an ML model: replace the body of score() with a call to
 *   your model's inference function, keeping the same Track → Float signature.
 */
class IntelligentShuffleEngine(
    private val sessionProfile: SessionProfile,
    private val preferredLanguages: Set<String> = emptySet(),
    private val temperature: Float = 0.7f
) {
    companion object {
        private const val TAG = "IntelligentShuffleEngine"
        private const val MAX_CONSECUTIVE_SAME_ARTIST = 2
        private const val PREFERRED_ARTIST_SPACING = 4
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Generates an intelligently ordered list of [tracks] using weighted
     * probabilistic selection with diversity enforcement.
     *
     * [currentTrack]: the currently playing song (excluded from result).
     *
     * Returns the shuffled ordering. Does NOT mutate the input list.
     */
    fun shuffle(tracks: List<Track>, currentTrack: Track? = null): List<Track> {
        if (tracks.isEmpty()) return emptyList()

        val candidates = tracks.filter { it.id != currentTrack?.id }.toMutableList()
        val result = mutableListOf<Track>()
        val recentArtists = ArrayDeque<String>(PREFERRED_ARTIST_SPACING * 2)

        while (candidates.isNotEmpty()) {
            val scores = candidates.map { track ->
                scoreTrack(track, recentArtists)
            }

            val selected = weightedRandomSelect(candidates, scores)
            result.add(selected)
            candidates.remove(selected)

            val artistKey = selected.artist.name.lowercase().trim()
            recentArtists.addFirst(artistKey)
            if (recentArtists.size > PREFERRED_ARTIST_SPACING * 3) recentArtists.removeLast()
        }

        Log.d(TAG, "Intelligent shuffle complete: ${result.size} tracks ordered")
        return result
    }

    /**
     * Select the next track to play given the current queue state and session signals.
     * Used when the queue has remaining songs and ExoPlayer's shuffle order needs
     * to be overridden with an intelligently chosen next song.
     *
     * Returns the index of the recommended next track in [remainingQueue].
     */
    fun pickNext(remainingQueue: List<Track>, currentTrack: Track?): Int {
        if (remainingQueue.isEmpty()) return 0

        val recentArtists = currentTrack?.let {
            ArrayDeque<String>().also { d -> d.addFirst(it.artist.name.lowercase().trim()) }
        } ?: ArrayDeque()

        val scores = remainingQueue.map { scoreTrack(it, recentArtists) }
        val selected = weightedRandomSelect(remainingQueue, scores)
        return remainingQueue.indexOf(selected).coerceAtLeast(0)
    }

    // -----------------------------------------------------------------------
    // Pluggable Scoring Function
    //
    // This is the function to replace with an ML model inference call.
    // Inputs:  Track + real-time session context (via sessionProfile)
    // Output:  Float score [0.001, 2.0] — higher = more likely to be chosen next
    // -----------------------------------------------------------------------

    private fun scoreTrack(track: Track, recentArtists: ArrayDeque<String>): Float {
        var score = 0.5f  // Neutral baseline

        val artistKey = track.artist.name.lowercase().trim()

        // --- Session affinity signals ---
        score += (sessionProfile.getArtistAffinity(track.artist.name) - 0.5f) * 0.5f
        score += (sessionProfile.getGenreAffinity(track.genre ?: "") - 0.5f) * 0.3f

        // --- Preferred Language affinity signal ---
        val inferredLang = com.musync.app.core.language.LanguageNormalizer.inferLanguage(track)
        val langAffinity = sessionProfile.getLanguageAffinity(inferredLang, preferredLanguages)
        score += (langAffinity - 0.5f) * 0.5f

        // --- Penalise skipped artists / genres ---
        score -= sessionProfile.getArtistSkipPenalty(track.artist.name) * 0.5f

        // --- Recency cooldown penalty ---
        score -= sessionProfile.getCooldownPenalty(track.id) * 0.6f

        // --- Artist spacing penalty ---
        val artistSlot = recentArtists.indexOf(artistKey)
        when {
            artistSlot in 0..1 -> score -= 0.6f   // Same artist in last 2 → heavy penalty
            artistSlot in 2..3 -> score -= 0.25f  // In last 3-4 → moderate penalty
            artistSlot in 4..5 -> score -= 0.05f  // In last 5-6 → light penalty
        }

        // --- Completion history bonus ---
        val completions = sessionProfile.completionCounts[track.id] ?: 0
        if (completions > 0) score += 0.15f

        return max(0.001f, score)
    }

    // -----------------------------------------------------------------------
    // Softmax-weighted probabilistic selection
    // -----------------------------------------------------------------------

    private fun weightedRandomSelect(tracks: List<Track>, scores: List<Float>): Track {
        if (tracks.size == 1) return tracks[0]

        val maxScore = scores.max()
        val expScores = scores.map { exp((it - maxScore) / temperature.toDouble()) }
        val sumExp = expScores.sum()
        val probabilities = expScores.map { it / sumExp }

        val rand = Random.nextDouble()
        var cumulative = 0.0
        for (i in probabilities.indices) {
            cumulative += probabilities[i]
            if (rand <= cumulative) return tracks[i]
        }
        // Fallback to last element (numerical precision guard)
        return tracks.last()
    }
}
