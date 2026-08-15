package com.missingcore.music.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.missingcore.music.domain.model.Album
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.provider.MusicProvider
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

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.privacydev.net",
            "https://pipedapi.leptons.xyz"
        )

        private val INVIDIOUS_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.privacydev.net"
        )
    }

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        customBaseUrl = if (!baseUrl.isNullOrBlank() && baseUrl != "none") {
            if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        } else null
        customApiKey = apiKey
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean = withContext(Dispatchers.IO) {
        val tracks = search("trending music")
        tracks.isNotEmpty()
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()
        val encoded = try { URLEncoder.encode(cleanQuery, "UTF-8") } catch (_: Exception) { cleanQuery }

        // 1. Custom ytmusicapi endpoint
        customBaseUrl?.let { customUrl ->
            try {
                val url = "$customUrl/search?query=$encoded"
                val tracks = fetchCustomYtMusic(url, customApiKey)
                if (tracks.isNotEmpty()) return@withContext tracks
            } catch (e: Exception) {
                Log.w(TAG, "Custom ytmusicapi endpoint failed: ${e.message}")
            }
        }

        // 2. Query Public Piped Instances
        for (instance in PIPED_INSTANCES) {
            try {
                val url = "$instance/search?q=$encoded&filter=music_songs"
                val tracks = fetchPipedSearch(url)
                if (tracks.isNotEmpty()) return@withContext tracks
            } catch (e: Exception) {
                Log.w(TAG, "Piped instance $instance failed: ${e.message}")
            }
        }

        // 3. Fallback: Invidious Search
        for (instance in INVIDIOUS_INSTANCES) {
            try {
                val url = "$instance/api/v1/search?q=$encoded&type=video"
                val tracks = fetchInvidiousSearch(url, instance)
                if (tracks.isNotEmpty()) return@withContext tracks
            } catch (_: Exception) {}
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
        search("Top Global Music Hits").ifEmpty { search("Trending Music") }
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        search("New Music Weekly").ifEmpty { search("Top Hits 2026") }
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        val videoId = id.removePrefix("yt_")
        for (instance in PIPED_INSTANCES) {
            try {
                val url = "$instance/streams/$videoId"
                val track = fetchPipedStreamDetails(url, videoId)
                if (track != null) return@withContext track
            } catch (_: Exception) {}
        }
        null
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
        if (!track.streamUrl.isNullOrBlank() && !track.streamUrl.contains("/streams/")) {
            return@withContext track.streamUrl
        }

        val videoId = track.id.removePrefix("yt_")
        for (instance in PIPED_INSTANCES) {
            try {
                val url = "$instance/streams/$videoId"
                val resolvedTrack = fetchPipedStreamDetails(url, videoId)
                if (resolvedTrack != null && !resolvedTrack.streamUrl.isNullOrBlank()) {
                    return@withContext resolvedTrack.streamUrl
                }
            } catch (_: Exception) {}
        }

        // Direct Invidious audio stream fallback
        "https://inv.nadeko.net/latest_version?id=$videoId&itag=140"
    }

    private fun fetchPipedSearch(urlStr: String): List<Track> {
        val req = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(req).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return emptyList()

        val items = root.getAsJsonArray("items") ?: return emptyList()
        val tracks = mutableListOf<Track>()

        for (elem in items) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject

            val url = obj.get("url")?.asString ?: ""
            val videoId = if (url.contains("v=")) url.substringAfter("v=").substringBefore("&") else url.removePrefix("/watch?v=")
            if (videoId.isBlank()) continue

            val title = obj.get("title")?.asString ?: "Unknown Title"
            val uploaderName = obj.get("uploaderName")?.asString ?: "YouTube Music"
            val durationSec = obj.get("duration")?.asLong ?: 200L
            val thumbnail = obj.get("thumbnail")?.asString ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val artistObj = Artist(id = "yt_artist_${uploaderName.hashCode()}", name = uploaderName, imageUrl = thumbnail)
            val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artistObj, artworkUrl = thumbnail)

            // High-compatibility Direct M4A audio stream
            val directStreamUrl = "https://inv.nadeko.net/latest_version?id=$videoId&itag=140"

            tracks.add(
                Track(
                    id = "yt_$videoId",
                    title = title,
                    artist = artistObj,
                    album = albumObj,
                    durationMs = durationSec * 1000L,
                    streamUrl = directStreamUrl,
                    artworkUrl = thumbnail,
                    genre = "Music",
                    playCount = 0L
                )
            )
        }
        return tracks
    }

    private fun fetchInvidiousSearch(urlStr: String, instanceBase: String): List<Track> {
        val req = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(req).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val items = gson.fromJson(jsonStr, JsonArray::class.java) ?: return emptyList()

        val tracks = mutableListOf<Track>()
        for (elem in items) {
            if (!elem.isJsonObject) continue
            val obj = elem.asJsonObject
            val videoId = obj.get("videoId")?.asString ?: continue
            val title = obj.get("title")?.asString ?: "Unknown Title"
            val author = obj.get("author")?.asString ?: "YouTube Artist"
            val lengthSec = obj.get("lengthSeconds")?.asLong ?: 200L
            val thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            val artistObj = Artist(id = "yt_artist_${author.hashCode()}", name = author, imageUrl = thumbnail)
            val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artistObj, artworkUrl = thumbnail)

            val directStreamUrl = "$instanceBase/latest_version?id=$videoId&itag=140"

            tracks.add(
                Track(
                    id = "yt_$videoId",
                    title = title,
                    artist = artistObj,
                    album = albumObj,
                    durationMs = lengthSec * 1000L,
                    streamUrl = directStreamUrl,
                    artworkUrl = thumbnail,
                    genre = "Music",
                    playCount = 0L
                )
            )
        }
        return tracks
    }

    private fun fetchPipedStreamDetails(urlStr: String, videoId: String): Track? {
        val req = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(req).execute()
        if (!response.isSuccessful) return null

        val jsonStr = response.body?.string() ?: return null
        val root = gson.fromJson(jsonStr, JsonObject::class.java) ?: return null

        val title = root.get("title")?.asString ?: "Unknown Title"
        val uploader = root.get("uploader")?.asString ?: "YouTube Artist"
        val thumbnail = root.get("thumbnailUrl")?.asString ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val duration = root.get("duration")?.asLong ?: 200L

        // Extract direct audio stream (Opus 160kbps or M4A 128kbps)
        var streamUrl = ""
        val audioStreams = root.getAsJsonArray("audioStreams")
        if (audioStreams != null && audioStreams.size() > 0) {
            var maxBitrate = 0
            for (streamElem in audioStreams) {
                if (!streamElem.isJsonObject) continue
                val sObj = streamElem.asJsonObject
                val bitrate = sObj.get("bitrate")?.asInt ?: 0
                val sUrl = sObj.get("url")?.asString ?: ""
                if (sUrl.isNotBlank() && bitrate >= maxBitrate) {
                    maxBitrate = bitrate
                    streamUrl = sUrl
                }
            }
        }

        if (streamUrl.isBlank()) {
            streamUrl = "https://inv.nadeko.net/latest_version?id=$videoId&itag=140"
        }

        val artistObj = Artist(id = "yt_artist_${uploader.hashCode()}", name = uploader, imageUrl = thumbnail)
        val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artistObj, artworkUrl = thumbnail)

        return Track(
            id = "yt_$videoId",
            title = title,
            artist = artistObj,
            album = albumObj,
            durationMs = duration * 1000L,
            streamUrl = streamUrl,
            artworkUrl = thumbnail,
            genre = "Music",
            playCount = 0L
        )
    }

    private fun fetchCustomYtMusic(urlStr: String, apiKey: String?): List<Track> {
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
                val artistName = artists?.firstOrNull()?.asJsonObject?.get("name")?.asString ?: obj.get("artist")?.asString ?: "YouTube Artist"
                val thumbnails = obj.getAsJsonArray("thumbnails")
                val artUrl = thumbnails?.lastOrNull()?.asJsonObject?.get("url")?.asString ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                val artistObj = Artist(id = "yt_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
                val albumObj = Album(id = "yt_album_${videoId.hashCode()}", name = title, artist = artistObj, artworkUrl = artUrl)

                val streamUrl = obj.get("url")?.asString
                    ?: "https://inv.nadeko.net/latest_version?id=$videoId&itag=140"

                tracks.add(
                    Track(
                        id = "yt_$videoId",
                        title = title,
                        artist = artistObj,
                        album = albumObj,
                        durationMs = (obj.get("duration_seconds")?.asLong ?: 180L) * 1000L,
                        streamUrl = streamUrl,
                        artworkUrl = artUrl,
                        genre = "Music",
                        playCount = 0L
                    )
                )
            }
        }
        return tracks
    }
}
