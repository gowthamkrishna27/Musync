package com.musync.app.data.remote

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.provider.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Universal YouTube Music Provider.
 * 
 * Supports:
 * 1. ytmusicapi Python backend (Search, Artists, Playlists, Tracks)
 * 2. High-speed Invidious & Piped Audio Gateways (Direct M4A / Opus audio streams)
 * 3. Native ExoPlayer playback
 */
class YouTubeMusicProvider(
    private var customBaseUrl: String? = null,
    private var customApiKey: String? = null
) : MusicProvider {

    override val providerId: String = "ytmusic"
    override val displayName: String = "YouTube Music"

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    companion object {
        private const val TAG = "YouTubeMusicProvider"
        const val RAILWAY_URL = "https://musync-production-2fc5.up.railway.app"
        const val LOCAL_URL = "http://192.168.0.104:5000"
        const val CLOUD_URL = RAILWAY_URL
        const val DEFAULT_RENDER_URL = RAILWAY_URL

        // NOTE: Piped and Invidious instance lists have been moved to the backend
        // (MusicProxyService). The Android client no longer calls these gateways directly.
    }

    @Volatile
    private var verifiedWorkingUrl: String? = null

    suspend fun getActiveBaseUrl(): String = withContext(Dispatchers.IO) {
        if (!customBaseUrl.isNullOrBlank() && customBaseUrl != "none") {
            return@withContext customBaseUrl!!.trimEnd('/')
        }

        verifiedWorkingUrl?.let { return@withContext it }

        // Default directly to 24/7 Railway Cloud Gateway
        verifiedWorkingUrl = RAILWAY_URL
        RAILWAY_URL
    }

    private var currentAudioQuality: String = "low"

    fun updateAudioQuality(quality: String) {
        currentAudioQuality = if (quality.isNotBlank()) quality else "low"
        Log.d(TAG, "Audio streaming quality updated -> $currentAudioQuality")
    }

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        val trimmed = baseUrl?.trim()?.trimEnd('/')
        customBaseUrl = if (!trimmed.isNullOrBlank() && trimmed != "none") trimmed else null
        customApiKey = apiKey
        verifiedWorkingUrl = null
        Log.d(TAG, "YouTubeMusicProvider configuration updated -> customBaseUrl: $customBaseUrl")
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean = withContext(Dispatchers.IO) {
        val target = (baseUrl ?: customBaseUrl ?: getActiveBaseUrl()).trimEnd('/')
        if (target.isBlank() || target == "none") return@withContext false
        try {
            val req = Request.Builder()
                .url("$target/health")
                .header("User-Agent", "Musync-Android/1.0")
                .apply {
                    if (!apiKey.isNullOrBlank()) header("X-API-Key", apiKey)
                }
                .build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) return@withContext true
        } catch (_: Exception) {}

        try {
            val req = Request.Builder()
                .url("$target/search?query=trending")
                .header("User-Agent", "Musync-Android/1.0")
                .build()
            val resp = httpClient.newCall(req).execute()
            resp.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Search for tracks via the Musync backend.
     *
     * All fallback tiers (Piped, Invidious) are now handled server-side inside
     * MusicProxyService — the client never calls third-party gateways directly.
     */
    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val encoded = try { URLEncoder.encode(cleanQuery, "UTF-8") } catch (_: Exception) { cleanQuery }

        val targetUrl = getActiveBaseUrl()
        try {
            val url = "$targetUrl/search?query=$encoded"
            val tracks = fetchCustomYtMusic(url, customApiKey, targetUrl)
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Backend search failed: ${e.message}")
            verifiedWorkingUrl = null
        }

        emptyList()
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        search(query).map { it.artist }.distinctBy { it.name }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        search("Latest Telugu Songs 2026").ifEmpty { search("Trending Telugu Songs") }
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        search("Telugu Melodies All Time Hits").ifEmpty { search("Telugu Romantic Songs") }
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        val videoId = id.removePrefix("yt_")
        val targetRenderUrl = getActiveBaseUrl()
        try {
            val url = "$targetRenderUrl/song?id=$videoId"
            val req = Request.Builder().url(url).header("User-Agent", "Musync-Android/1.0").build()
            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val obj = gson.fromJson(body, JsonObject::class.java)
                    val title = obj.get("title")?.asString ?: "YouTube Track"
                    val artistName = obj.get("artist")?.asString ?: "YouTube Artist"
                    val artUrl = obj.get("image_url")?.asString ?: "https://i.ytimg.com/vi/$videoId/mqdefault.jpg"
                    val artist = Artist(id = "yt_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
                    val album = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artist, artworkUrl = artUrl)
                    val streamUrl = "$targetRenderUrl/stream?id=$videoId"
                    return@withContext Track(
                        id = "yt_$videoId",
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = 180000L,
                        streamUrl = streamUrl,
                        artworkUrl = artUrl,
                        genre = "Music"
                    )
                }
            }
        } catch (_: Exception) {}

        // Fallback: construct standard Track with targetRenderUrl/stream
        val artUrl = "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val artist = Artist(id = "yt_artist_$videoId", name = "YouTube Music", imageUrl = artUrl)
        val album = Album(id = "yt_album_$videoId", name = "Single", artist = artist, artworkUrl = artUrl)
        Track(
            id = "yt_$videoId",
            title = "YouTube Music Track",
            artist = artist,
            album = album,
            durationMs = 180000L,
            streamUrl = "$targetRenderUrl/stream?id=$videoId",
            artworkUrl = artUrl,
            genre = "Music"
        )
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        Artist(id = id, name = id.removePrefix("yt_artist_"))
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        search(artistId.removePrefix("yt_artist_"))
    }

    override suspend fun getAlbum(id: String): Album? = withContext(Dispatchers.IO) {
        val tracks = search(id.removePrefix("yt_album_"))
        val first = tracks.firstOrNull()
        if (first != null) {
            Album(
                id = id,
                name = first.album?.name ?: id,
                artist = first.artist,
                artworkUrl = first.artworkUrl,
                trackCount = tracks.size
            )
        } else null
    }

    override suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        val tracks = search("playlist")
        if (tracks.isNotEmpty()) {
            Playlist(
                id = id,
                name = "YouTube Music Mix",
                description = "Featured streaming playlist",
                artworkUrl = tracks.firstOrNull()?.artworkUrl,
                tracks = tracks,
                isCustom = false
            )
        } else null
    }

    override suspend fun getStreamUrl(track: Track): String? = withContext(Dispatchers.IO) {
        val videoId = track.id.removePrefix("yt_")
        val targetRenderUrl = getActiveBaseUrl()

        // 1. Query Render /song endpoint with 3s fast timeout
        try {
            val songUrl = "$targetRenderUrl/song?id=$videoId"
            val reqBuilder = Request.Builder().url(songUrl).header("User-Agent", "Musync-Android/1.0")
            customApiKey?.let { if (it.isNotBlank()) reqBuilder.header("Authorization", "Bearer $it") }
            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val obj = gson.fromJson(body, JsonObject::class.java)
                    val directUrl = obj.get("url")?.asString ?: obj.get("media_url")?.asString ?: obj.get("stream_url")?.asString
                    if (!directUrl.isNullOrBlank() && (directUrl.startsWith("http://") || directUrl.startsWith("https://"))) {
                        return@withContext directUrl
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed resolving /song direct stream: ${e.message}")
        }

        // 2. Return direct high-availability server audio stream redirect
        "$targetRenderUrl/stream?id=$videoId&quality=$currentAudioQuality"
    }

    suspend fun getLyrics(id: String): String? = withContext(Dispatchers.IO) {
        val videoId = id.removePrefix("yt_")
        val targetRenderUrl = getActiveBaseUrl()
        try {
            val url = "$targetRenderUrl/lyrics?id=$videoId"
            val reqBuilder = Request.Builder().url(url).header("User-Agent", "Musync-Android/1.0")
            customApiKey?.let { if (it.isNotBlank()) reqBuilder.header("Authorization", "Bearer $it") }
            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val obj = gson.fromJson(body, JsonObject::class.java)
                    return@withContext obj.get("lyrics")?.asString
                }
            }
        } catch (_: Exception) {}
        null
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val targetRenderUrl = getActiveBaseUrl()
        val encoded = try { URLEncoder.encode(clean, "UTF-8") } catch (_: Exception) { clean }
        try {
            val url = "$targetRenderUrl/suggestions?query=$encoded"
            val reqBuilder = Request.Builder().url(url).header("User-Agent", "Musync-Android/1.0")
            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val arr = gson.fromJson(body, JsonArray::class.java)
                    return@withContext arr.mapNotNull { it.asString }
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    private fun fetchCustomYtMusic(urlStr: String, apiKey: String?, activeBaseUrl: String): List<Track> {
        val reqBuilder = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Musync-Android/1.0")
            .header("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
        }

        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val element = gson.fromJson(jsonStr, JsonElement::class.java) ?: return emptyList()

        val tracks = mutableListOf<Track>()
        if (element.isJsonArray) {
            for (elem in element.asJsonArray) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val videoId = obj.get("videoId")?.asString ?: obj.get("id")?.asString ?: continue
                val title = obj.get("title")?.asString ?: "Unknown Title"
                val artists = obj.getAsJsonArray("artists")
                val artistElem = obj.get("artist")
                val artistName = when {
                    artistElem != null && artistElem.isJsonObject -> artistElem.asJsonObject.get("name")?.asString ?: "YouTube Artist"
                    artistElem != null && artistElem.isJsonPrimitive -> artistElem.asString
                    artists != null && artists.size() > 0 -> artists.firstOrNull()?.asJsonObject?.get("name")?.asString ?: "YouTube Artist"
                    else -> "YouTube Artist"
                }

                val artworkElem = obj.get("artwork")
                val artUrl = when {
                    artworkElem != null && artworkElem.isJsonObject ->
                        artworkElem.asJsonObject.get("large")?.asString ?: artworkElem.asJsonObject.get("medium")?.asString ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    obj.has("image_url") -> obj.get("image_url")?.asString ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    obj.has("image") -> obj.get("image")?.asString ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    else -> {
                        val thumbnails = obj.getAsJsonArray("thumbnails")
                        thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                    }
                }

                val artistObj = Artist(id = "yt_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
                val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artistObj, artworkUrl = artUrl)

                val streamUrl = "$activeBaseUrl/stream?id=$videoId"

                tracks.add(
                    Track(
                        id = "yt_$videoId",
                        title = title,
                        artist = artistObj,
                        album = albumObj,
                        durationMs = (obj.get("duration_seconds")?.asLong ?: obj.get("duration")?.asLong ?: 180L) * 1000L,
                        streamUrl = streamUrl,
                        artworkUrl = artUrl,
                        genre = obj.get("language")?.asString ?: obj.get("genre")?.asString ?: "Music",
                        playCount = 0L
                    )
                )
            }
        }
        return tracks
    }

    suspend fun getDiscoverTrending(region: String = "global", language: String = "All"): List<Track> = withContext(Dispatchers.IO) {
        val targetRenderUrl = getActiveBaseUrl()
        try {
            val encodedLang = URLEncoder.encode(language, "UTF-8")
            val url = "$targetRenderUrl/api/discover/trending?region=$region&language=$encodedLang"
            val tracks = fetchCustomYtMusic(url, customApiKey, targetRenderUrl)
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Discover trending failed for $language: ${e.message}")
        }
        val query = if (language != "All") "Trending $language Songs" else "Trending Global Songs"
        search(query)
    }

    suspend fun getDiscoverNew(language: String = "All"): List<Track> = withContext(Dispatchers.IO) {
        val targetRenderUrl = getActiveBaseUrl()
        try {
            val encodedLang = URLEncoder.encode(language, "UTF-8")
            val url = "$targetRenderUrl/api/discover/new?language=$encodedLang"
            val tracks = fetchCustomYtMusic(url, customApiKey, targetRenderUrl)
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Discover new failed for $language: ${e.message}")
        }
        val query = if (language != "All") "Latest New $language Songs" else "Latest New Release Songs"
        search(query)
    }

    suspend fun getDiscoverRising(): List<Track> = withContext(Dispatchers.IO) {
        val targetRenderUrl = getActiveBaseUrl()
        try {
            val url = "$targetRenderUrl/api/discover/rising"
            val tracks = fetchCustomYtMusic(url, customApiKey, targetRenderUrl)
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.w(TAG, "Discover rising failed: ${e.message}")
        }
        search("Breakout Viral Hits 2026")
    }

    override suspend fun getRecommendations(trackId: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("yt_").trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val targetRenderUrl = getActiveBaseUrl()
        try {
            val url = "$targetRenderUrl/recommendations?trackId=$cleanId&limit=$limit"
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Musync-Android/1.0")
                .header("Accept", "application/json")
            if (!customApiKey.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $customApiKey")
            }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (response.isSuccessful) {
                val body = response.body?.string()
                if (!body.isNullOrBlank()) {
                    val root = gson.fromJson(body, JsonElement::class.java)
                    val recArray = when {
                        root.isJsonObject && root.asJsonObject.has("recommendations") ->
                            root.asJsonObject.getAsJsonArray("recommendations")
                        root.isJsonArray -> root.asJsonArray
                        else -> null
                    }

                    if (recArray != null && recArray.size() > 0) {
                        val tracks = mutableListOf<Track>()
                        for (elem in recArray) {
                            if (!elem.isJsonObject) continue
                            val obj = elem.asJsonObject
                            val videoId = obj.get("videoId")?.asString ?: obj.get("id")?.asString ?: continue
                            // Exclude current track just in case
                            if (videoId == cleanId) continue

                            val title = obj.get("title")?.asString ?: obj.get("song")?.asString ?: "Unknown Title"
                            val artistName = obj.get("artist")?.asString ?: obj.get("singers")?.asString ?: "YouTube Artist"
                            val albumName = obj.get("album")?.asString ?: title
                            val artUrl = obj.get("image_url")?.asString
                                ?: obj.get("image")?.asString
                                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
                            val durationSec = obj.get("duration_seconds")?.asLong
                                ?: (obj.get("duration")?.asString?.toLongOrNull() ?: 180L)
                            val streamUrl = obj.get("stream_url")?.asString
                                ?: obj.get("url")?.asString
                                ?: "$targetRenderUrl/stream?id=$videoId"

                            val artistObj = Artist(id = "yt_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
                            val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = albumName, artist = artistObj, artworkUrl = artUrl)

                            tracks.add(
                                Track(
                                    id = "yt_$videoId",
                                    title = title,
                                    artist = artistObj,
                                    album = albumObj,
                                    durationMs = durationSec * 1000L,
                                    streamUrl = streamUrl,
                                    artworkUrl = artUrl,
                                    genre = "Music",
                                    playCount = 0L
                                )
                            )
                        }
                        if (tracks.isNotEmpty()) return@withContext tracks
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Primary recommendations endpoint unavailable for $cleanId: ${e.message}")
        }

        // Resilient Fallback: If /recommendations is not available on active cloud backend,
        // intelligently fetch related recommendations via artist/track search or trending.
        try {
            val trackInfo = getTrack(cleanId)
            val searchQueries = mutableListOf<String>()
            if (trackInfo != null) {
                val artistName = trackInfo.artist.name.trim()
                if (artistName.isNotBlank() && !artistName.equals("YouTube Artist", ignoreCase = true) && !artistName.equals("YouTube Music", ignoreCase = true)) {
                    searchQueries.add(artistName)
                }
                val title = trackInfo.title.trim()
                if (title.isNotBlank() && !title.equals("YouTube Track", ignoreCase = true) && !title.equals("YouTube Music Track", ignoreCase = true)) {
                    searchQueries.add(title)
                }
            }

            for (query in searchQueries) {
                val results = search(query)
                val filtered = results.filter { it.id != "yt_$cleanId" && it.id != cleanId }
                if (filtered.isNotEmpty()) {
                    return@withContext filtered.take(limit)
                }
            }

            // Ultimate fallback: trending songs
            val trending = getTrending()
            val filteredTrending = trending.filter { it.id != "yt_$cleanId" && it.id != cleanId }
            if (filteredTrending.isNotEmpty()) {
                return@withContext filteredTrending.take(limit)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallback recommendations failed for $cleanId: ${e.message}")
        }

        emptyList()
    }
}


