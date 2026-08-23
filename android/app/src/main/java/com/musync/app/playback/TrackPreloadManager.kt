package com.musync.app.playback

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.musync.app.domain.model.Track
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Next-Track Preload Pipeline for Continuous & Gapless Playback.
 *
 * Pipeline Architecture:
 * - Current Track (N): PLAYING (Authoritative ExoPlayer)
 * - Next Track (N+1): PRELOADING (Pre-warms Redis & Caches initial 256KB chunk to SimpleCache)
 * - Following Track (N+2): PRE-RESOLVING (Pre-warms backend resolution cache in Redis)
 *
 * Result:
 * When Track N finishes, Track N+1's stream header and initial 256KB are already present
 * in the local Media3 SimpleCache, yielding immediate playback start with ~0ms transition gap.
 */
@OptIn(UnstableApi::class)
class TrackPreloadManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "TrackPreloadManager"
        private const val PRELOAD_CHUNK_SIZE_WIFI = 512L * 1024L   // 512 KB — larger initial buffer head-start on Wi-Fi
        private const val PRELOAD_CHUNK_SIZE_MOBILE = 128L * 1024L // 128 KB
        private const val PRELOAD_STALE_MS = 90L * 60L * 1000L    // Preload keys expire after 90 minutes
    }

    // Maps videoId -> timestamp of last successful preload.
    // Using videoId (not full URL) so entries survive URL rotation.
    // Keyed with a 90-minute expiry bucket to auto-invalidate stale preloads.
    private val preloadedUrls = ConcurrentHashMap<String, Long>()
    private var currentPreloadJob: Job? = null

    private val cache = MediaCacheManager.getCache(context)
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setConnectTimeoutMs(10000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("Musync-Android-Preload/1.0")

    private val cacheDataSourceFactory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(httpFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /**
     * Called when a track starts playing. Initiates polite background preloading of next track.
     */
    fun onTrackPlaying(
        currentIndex: Int,
        queue: List<Track>,
        baseUrl: String = "https://musync-8482.onrender.com",
        quality: String = "low"
    ) {
        if (queue.isEmpty() || currentIndex !in queue.indices) return

        currentPreloadJob?.cancel()
        currentPreloadJob = scope.launch(Dispatchers.IO) {
            try {
                // Give active playing track 100% priority to build initial buffer
                delay(2500L)

                // 1. Next Track (N+1) -> Preload initial audio bytes to local SimpleCache
                val nextIndex = currentIndex + 1
                if (nextIndex < queue.size) {
                    val nextTrack = queue[nextIndex]
                    preloadTrackAudio(nextTrack, baseUrl, quality)
                }

                // 2. Following Track (N+2) -> Pre-resolve backend stream URL in Redis
                val followingIndex = currentIndex + 2
                if (followingIndex < queue.size) {
                    val followingTrack = queue[followingIndex]
                    preresolveTrackInRedis(followingTrack, baseUrl, quality)
                }
            } catch (e: CancellationException) {
                // Normal job replacement on track skip
            } catch (e: Exception) {
                Log.w(TAG, "Preload pipeline error: ${e.message}")
            }
        }
    }

    /**
     * Preloads the initial 128KB - 256KB chunk of the track directly into Media3 SimpleCache.
     */
    private suspend fun preloadTrackAudio(
        track: Track,
        baseUrl: String,
        quality: String
    ) = withContext(Dispatchers.IO) {
        val videoId = track.id.removePrefix("yt_")
        val streamUrl = track.streamUrl ?: "$baseUrl/stream?id=$videoId&quality=$quality"

        // Dedup by videoId + 90-minute time bucket so stale preloads are invalidated
        // without relying on the URL being stable across Redis flushes or server restarts.
        val nowMs = System.currentTimeMillis()
        val lastPreloadMs = preloadedUrls[videoId] ?: 0L
        if (nowMs - lastPreloadMs < PRELOAD_STALE_MS) {
            Log.d(TAG, "Track '${track.title}' ($videoId) is already preloaded and still fresh (age: ${(nowMs - lastPreloadMs) / 1000}s).")
            return@withContext
        }

        val startTime = System.currentTimeMillis()
        val uri = Uri.parse(streamUrl)
        val dataSource = cacheDataSourceFactory.createDataSource()

        val isWifi = com.musync.app.core.network.NetworkQualityHelper.isWifiConnected(context)
        val chunkSize = if (isWifi) PRELOAD_CHUNK_SIZE_WIFI else PRELOAD_CHUNK_SIZE_MOBILE
        val dataSpec = DataSpec.Builder()
            .setUri(uri)
            .setPosition(0L)
            .setLength(chunkSize)
            .build()

        try {
            dataSource.open(dataSpec)
            val buffer = ByteArray(32 * 1024)
            var totalRead = 0L
            while (totalRead < chunkSize) {
                val toRead = ((chunkSize - totalRead).coerceAtMost(buffer.size.toLong())).toInt()
                val read = dataSource.read(buffer, 0, toRead)
                if (read <= 0) break
                totalRead += read
            }
            dataSource.close()

            preloadedUrls[videoId] = System.currentTimeMillis()
            val duration = System.currentTimeMillis() - startTime
            Log.d(
                TAG,
                "✓ Preloaded $totalRead bytes for Next Track: '${track.title}' (${track.id}) in ${duration}ms -> Ready for 0ms transition"
            )
        } catch (e: Exception) {
            try { dataSource.close() } catch (_: Exception) {}
            Log.w(TAG, "Failed preloading audio for '${track.title}' (${track.id}): ${e.message}")
        }
    }

    /**
     * Proactively triggers stream resolution on the backend so Redis has the URL warm.
     */
    private suspend fun preresolveTrackInRedis(
        track: Track,
        baseUrl: String,
        quality: String
    ) = withContext(Dispatchers.IO) {
        val videoId = track.id.removePrefix("yt_")
        try {
            val preloadApiUrl = java.net.URL("$baseUrl/stream/preload?id=$videoId&quality=$quality")
            val connection = preloadApiUrl.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Musync-Android-Preload/1.0")

            val responseCode = connection.responseCode
            connection.disconnect()
            Log.d(TAG, "✓ Pre-resolved in Redis for Following Track: '${track.title}' (${track.id}) [HTTP $responseCode]")
        } catch (e: Exception) {
            Log.w(TAG, "Failed pre-resolving '${track.title}' in Redis: ${e.message}")
        }
    }

    fun clear() {
        currentPreloadJob?.cancel()
        preloadedUrls.clear()
    }
}
