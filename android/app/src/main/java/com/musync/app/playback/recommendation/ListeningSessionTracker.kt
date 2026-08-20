package com.musync.app.playback.recommendation

import android.util.Log
import com.musync.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Tracks real-time listening milestones and emits signals to:
 *  1. The local [SessionProfile] (instant, in-memory)
 *  2. The backend session service (async, fire-and-forget)
 *
 * Milestone events:
 *  - SONG_STARTED      : track begins playing
 *  - SONG_25_PERCENT   : 25% of track duration reached
 *  - SONG_50_PERCENT   : 50% of track duration reached
 *  - SONG_75_PERCENT   : 75% of track duration reached (also triggers next-song pre-generation)
 *  - SONG_COMPLETED    : playback reached >= 95% of duration before skipping
 *  - SONG_SKIPPED      : track was skipped before 25% completion
 *  - SONG_REPLAYED     : user replayed from the beginning
 *  - SONG_LIKED        : user explicitly liked the track
 *  - SONG_UNLIKED      : user explicitly removed like
 *
 * This class is designed to be called from the PlaybackManager's progress tracker
 * and player listener. It does NOT touch ExoPlayer or the MediaController directly.
 */
class ListeningSessionTracker(
    private val scope: CoroutineScope,
    val sessionProfile: SessionProfile = SessionProfile()
) {
    companion object {
        private const val TAG = "ListeningSessionTracker"
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Per-track state
    private var currentTrackId: String? = null
    private var currentTrackDurationMs: Long = 0L
    private var currentArtistName: String = ""
    private var currentGenre: String? = null

    // Milestone flags (reset on each track change)
    private var emitted25 = false
    private var emitted50 = false
    private var emitted75 = false
    private var emittedStart = false
    private var trackStartMs = 0L

    // Holds the base URL for backend event posting
    var backendBaseUrl: String = ""
    var userId: String = "anonymous"

    // -----------------------------------------------------------------------
    // Track transition lifecycle
    // -----------------------------------------------------------------------

    /**
     * Call this when a new track begins playing.
     */
    fun onTrackStarted(track: Track) {
        // If switching away from a previously started track, determine skip or completion
        currentTrackId?.let { prevId ->
            if (emittedStart && !emitted75) {
                // Skipped before 75% — record as skip
                emitSkip(prevId, currentArtistName, currentGenre)
            }
        }

        // Reset milestone flags
        emitted25 = false
        emitted50 = false
        emitted75 = false
        emittedStart = false
        trackStartMs = System.currentTimeMillis()

        currentTrackId = track.id
        currentTrackDurationMs = track.durationMs ?: 0L
        currentArtistName = track.artist.name
        currentGenre = track.genre

        // Emit SONG_STARTED
        emittedStart = true
        sessionProfile.onTrackStarted(track.id, track.artist.name)
        postEventAsync("SONG_STARTED", track.id, track.artist.name, track.genre)
        Log.d(TAG, "▶ SONG_STARTED: '${track.title}' by ${track.artist.name}")
    }

    /**
     * Call this on every progress tick (from PlaybackManager's 500ms loop).
     * positionMs: current playback position
     * durationMs: total track duration
     */
    fun onProgressUpdate(trackId: String, positionMs: Long, durationMs: Long) {
        if (trackId != currentTrackId || durationMs <= 0) return

        currentTrackDurationMs = durationMs
        val pct = (positionMs.toFloat() / durationMs.toFloat() * 100f)

        if (!emitted25 && pct >= 25f) {
            emitted25 = true
            postEventAsync("SONG_25_PERCENT", trackId, currentArtistName, currentGenre, positionMs)
            Log.d(TAG, "📍 25%: $trackId")
        }
        if (!emitted50 && pct >= 50f) {
            emitted50 = true
            postEventAsync("SONG_50_PERCENT", trackId, currentArtistName, currentGenre, positionMs)
            Log.d(TAG, "📍 50%: $trackId")
        }
        if (!emitted75 && pct >= 75f) {
            emitted75 = true
            sessionProfile.on75Percent(currentArtistName, currentGenre)
            postEventAsync("SONG_75_PERCENT", trackId, currentArtistName, currentGenre, positionMs)
            Log.d(TAG, "📍 75%: $trackId — triggering next-song pre-generation")
        }
    }

    /**
     * Call this when the track reaches its natural end (STATE_ENDED or transition without skip).
     */
    fun onTrackCompleted(trackId: String) {
        if (trackId != currentTrackId) return
        sessionProfile.onTrackCompleted(trackId, currentArtistName, currentGenre)
        postEventAsync("SONG_COMPLETED", trackId, currentArtistName, currentGenre)
        Log.d(TAG, "✅ SONG_COMPLETED: $trackId")
    }

    /**
     * Call this when the user explicitly likes a track.
     */
    fun onTrackLiked(track: Track) {
        sessionProfile.onTrackLiked(track.artist.name, track.genre)
        postEventAsync("SONG_LIKED", track.id, track.artist.name, track.genre)
        Log.d(TAG, "❤️ SONG_LIKED: ${track.id}")
    }

    /**
     * Call this when the user removes a like.
     */
    fun onTrackUnliked(track: Track) {
        sessionProfile.onTrackUnliked(track.artist.name, track.genre)
        postEventAsync("SONG_UNLIKED", track.id, track.artist.name, track.genre)
        Log.d(TAG, "💔 SONG_UNLIKED: ${track.id}")
    }

    /**
     * Call this when the user replays (seeks back to 0 or replay action).
     */
    fun onTrackReplayed(track: Track) {
        sessionProfile.onTrackReplayed(track.id, track.artist.name, track.genre)
        postEventAsync("SONG_REPLAYED", track.id, track.artist.name, track.genre)
        Log.d(TAG, "🔁 SONG_REPLAYED: ${track.id}")
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun emitSkip(trackId: String, artistName: String, genre: String?) {
        sessionProfile.onTrackSkipped(trackId, artistName, genre)
        postEventAsync("SONG_SKIPPED", trackId, artistName, genre)
        Log.d(TAG, "⏭ SONG_SKIPPED: $trackId")
    }

    private fun postEventAsync(
        eventType: String,
        trackId: String,
        artistName: String,
        genre: String?,
        positionMs: Long = 0L
    ) {
        if (backendBaseUrl.isBlank()) return

        scope.launch(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("userId", userId)
                    put("trackId", trackId.removePrefix("yt_"))
                    put("artistName", artistName)
                    if (!genre.isNullOrBlank()) put("genre", genre)
                    put("eventType", eventType)
                    put("positionMs", positionMs)
                    put("durationMs", currentTrackDurationMs)
                }.toString()

                val request = Request.Builder()
                    .url("$backendBaseUrl/api/listening-events")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", "Musync-Android/1.0")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Event post failed: ${response.code} for $eventType")
                    }
                }
            } catch (e: Exception) {
                // Fire-and-forget: never let network failures affect playback
                Log.w(TAG, "Failed to post $eventType event: ${e.message}")
            }
        }
    }
}
