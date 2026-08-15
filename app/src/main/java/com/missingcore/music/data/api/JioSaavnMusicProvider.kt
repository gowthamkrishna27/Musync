package com.missingcore.music.data.api

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.missingcore.music.data.api.dto.JioSaavnSongDto
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Universal Resilient JioSaavn Music Provider.
 * 
 * Supports:
 * 1. User's custom JioSaavn API endpoint (Flask / FastAPI / Vercel instance)
 * 2. High-availability public JioSaavn endpoints (saavn.dev, vercel mirrors)
 * 3. Direct JioSaavn Gateway Fallback (api.php)
 * 
 * Guarantees that music search, trending tracks, HD artwork (500x500), and MP3 audio streams
 * fetch reliably with zero mandatory local server setup.
 */
class JioSaavnMusicProvider(
    private var customBaseUrl: String? = null,
    private var customApiKey: String? = null
) : MusicProvider {

    override val providerId: String = "jiosaavn"
    override val displayName: String = "JioSaavn Music"

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    companion object {
        private const val TAG = "JioSaavnProvider"
        
        // High-availability public endpoints
        private val PUBLIC_MIRRORS = listOf(
            "https://saavn.dev/api",
            "https://jiosaavn-api-privateindexer.vercel.app",
            "https://jiosaavn-api-sigma-five.vercel.app"
        )
    }

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        customBaseUrl = if (!baseUrl.isNullOrBlank() && baseUrl != "none") {
            if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
        } else null
        customApiKey = apiKey
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean = withContext(Dispatchers.IO) {
        val tracks = search("latest")
        tracks.isNotEmpty()
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val encodedQuery = try { URLEncoder.encode(query.trim(), "UTF-8") } catch (_: Exception) { query.trim() }

        // 1. Try Custom Endpoint if configured by user
        customBaseUrl?.let { customUrl ->
            try {
                val url = "$customUrl/result/?query=$encodedQuery&lyrics=false"
                val tracks = fetchAndParse(url, customApiKey)
                if (tracks.isNotEmpty()) return@withContext tracks
            } catch (e: Exception) {
                Log.w(TAG, "Custom endpoint failed: ${e.message}")
            }
        }

        // 2. Try Public Saavn API Mirrors
        for (mirror in PUBLIC_MIRRORS) {
            try {
                // Try standard /result/?query= or /search/songs?query=
                val url = if (mirror.contains("saavn.dev")) {
                    "$mirror/search/songs?query=$encodedQuery"
                } else {
                    "$mirror/result/?query=$encodedQuery&lyrics=false"
                }
                val tracks = fetchAndParse(url, null)
                if (tracks.isNotEmpty()) return@withContext tracks
            } catch (e: Exception) {
                Log.w(TAG, "Mirror $mirror failed: ${e.message}")
            }
        }

        // 3. Fallback: Direct JioSaavn Search API
        try {
            val directUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&ctx=android&api_version=4&q=$encodedQuery&p=1&n=30"
            val tracks = fetchDirectSaavn(directUrl)
            if (tracks.isNotEmpty()) return@withContext tracks
        } catch (e: Exception) {
            Log.e(TAG, "Direct Saavn search failed: ${e.message}")
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
        search("Trending India").ifEmpty { search("Top Songs") }.ifEmpty { search("Hindi") }
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        search("Punjabi Hits").ifEmpty { search("Telugu Hits") }.ifEmpty { search("English Hits") }
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        val cleanId = id.removePrefix("saavn_")
        // Try direct song query
        customBaseUrl?.let { customUrl ->
            try {
                val tracks = fetchAndParse("$customUrl/song/?query=$cleanId&lyrics=true", customApiKey)
                if (tracks.isNotEmpty()) return@withContext tracks.first()
            } catch (_: Exception) {}
        }

        for (mirror in PUBLIC_MIRRORS) {
            try {
                val url = if (mirror.contains("saavn.dev")) "$mirror/songs?id=$cleanId" else "$mirror/song/?query=$cleanId&lyrics=true"
                val tracks = fetchAndParse(url, null)
                if (tracks.isNotEmpty()) return@withContext tracks.first()
            } catch (_: Exception) {}
        }

        search(cleanId).firstOrNull()
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        Artist(id = id, name = id.removePrefix("saavn_artist_"))
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        search(artistId.removePrefix("saavn_artist_"))
    }

    override suspend fun getAlbum(id: String): Album? = withContext(Dispatchers.IO) {
        val tracks = search(id.removePrefix("saavn_album_"))
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
                name = "JioSaavn Top Tracks",
                description = "Featured streaming playlist",
                artworkUrl = tracks.firstOrNull()?.artworkUrl,
                tracks = tracks,
                isCustom = false
            )
        } else null
    }

    override suspend fun getStreamUrl(track: Track): String? {
        return if (!track.streamUrl.isNullOrBlank()) track.streamUrl else null
    }

    private fun fetchAndParse(urlStr: String, apiKey: String?): List<Track> {
        val reqBuilder = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Musync-Android/1.0")
            .header("Accept", "application/json")
        if (!apiKey.isNullOrBlank()) {
            reqBuilder.header("Authorization", "Bearer $apiKey")
            reqBuilder.header("x-api-key", apiKey)
        }

        val response = httpClient.newCall(reqBuilder.build()).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val element = gson.fromJson(jsonStr, JsonElement::class.java) ?: return emptyList()

        return parseUniversalJson(element)
    }

    private fun fetchDirectSaavn(urlStr: String): List<Track> {
        val req = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .build()

        val response = httpClient.newCall(req).execute()
        if (!response.isSuccessful) return emptyList()

        val jsonStr = response.body?.string() ?: return emptyList()
        val element = gson.fromJson(jsonStr, JsonElement::class.java) ?: return emptyList()

        val tracks = mutableListOf<Track>()
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            val results = obj.getAsJsonArray("results") ?: return emptyList()
            for (item in results) {
                if (item.isJsonObject) {
                    val trackObj = parseDirectSaavnItem(item.asJsonObject)
                    if (trackObj != null) tracks.add(trackObj)
                }
            }
        }
        return tracks
    }

    private fun parseDirectSaavnItem(item: JsonObject): Track? {
        val title = item.get("song")?.asString ?: item.get("title")?.asString ?: return null
        val id = item.get("id")?.asString ?: item.get("songid")?.asString ?: UUID.randomUUID().toString()
        val artistName = item.get("primary_artists")?.asString ?: item.get("singers")?.asString ?: "Unknown Artist"
        val albumName = item.get("album")?.asString ?: title
        val rawImage = item.get("image")?.asString ?: item.get("image_url")?.asString
        val artUrl = rawImage?.replace("150x150", "500x500")?.replace("http://", "https://")

        val durationSec = item.get("duration")?.asString?.toIntOrNull() ?: 180
        val durationMs = durationSec * 1000L

        // Extract streaming URL with DES Decryption fallback
        var streamUrl = JioSaavnDecryptor.decryptMediaUrl(item.get("encrypted_media_url")?.asString)
            ?: item.get("media_preview_url")?.asString
            ?: item.get("url")?.asString
            ?: item.get("media_url")?.asString
            ?: ""

        streamUrl = JioSaavnDecryptor.formatStreamUrl(streamUrl)

        val artistObj = Artist(id = "saavn_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
        val albumObj = Album(id = "saavn_album_${albumName.hashCode()}", name = albumName, artist = artistObj, artworkUrl = artUrl)

        return Track(
            id = "saavn_$id",
            title = title,
            artist = artistObj,
            album = albumObj,
            durationMs = durationMs,
            streamUrl = streamUrl,
            artworkUrl = artUrl,
            genre = item.get("language")?.asString?.replaceFirstChar { it.uppercase() } ?: "Music",
            playCount = 0L
        )
    }

    private fun parseUniversalJson(element: JsonElement): List<Track> {
        val tracks = mutableListOf<Track>()

        // 1. If element is Array: [ {...}, {...} ] (User's Flask JioSaavn API)
        if (element.isJsonArray) {
            val listType = object : TypeToken<List<JioSaavnSongDto>>() {}.type
            val dtoList: List<JioSaavnSongDto>? = try { gson.fromJson(element, listType) } catch (_: Exception) { null }
            if (dtoList != null) {
                tracks.addAll(dtoList.mapNotNull { it.toTrack() })
            }
            return tracks
        }

        // 2. If element is Object: { "data": [...] } or { "results": [...] } or { "data": { "results": [...] } }
        if (element.isJsonObject) {
            val root = element.asJsonObject

            // Handle { "data": { "results": [ ... ] } } (saavn.dev schema)
            if (root.has("data")) {
                val dataElem = root.get("data")
                if (dataElem.isJsonArray) {
                    tracks.addAll(parseUniversalSongArray(dataElem.asJsonArray))
                } else if (dataElem.isJsonObject) {
                    val dataObj = dataElem.asJsonObject
                    if (dataObj.has("results") && dataObj.get("results").isJsonArray) {
                        tracks.addAll(parseUniversalSongArray(dataObj.getAsJsonArray("results")))
                    } else if (dataObj.has("songs") && dataObj.get("songs").isJsonArray) {
                        tracks.addAll(parseUniversalSongArray(dataObj.getAsJsonArray("songs")))
                    }
                }
            }

            // Handle { "results": [ ... ] }
            if (tracks.isEmpty() && root.has("results") && root.get("results").isJsonArray) {
                tracks.addAll(parseUniversalSongArray(root.getAsJsonArray("results")))
            }

            // Handle single song object
            if (tracks.isEmpty()) {
                val singleDto: JioSaavnSongDto? = try { gson.fromJson(root, JioSaavnSongDto::class.java) } catch (_: Exception) { null }
                val singleTrack = singleDto?.toTrack()
                if (singleTrack != null) tracks.add(singleTrack)
            }
        }

        return tracks
    }

    private fun parseUniversalSongArray(array: JsonArray): List<Track> {
        val list = mutableListOf<Track>()
        for (item in array) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject

            // Try DTO format first
            val dto: JioSaavnSongDto? = try { gson.fromJson(obj, JioSaavnSongDto::class.java) } catch (_: Exception) { null }
            val trackFromDto = dto?.toTrack()
            if (trackFromDto != null && !trackFromDto.streamUrl.isNullOrBlank()) {
                list.add(trackFromDto)
                continue
            }

            // Handle saavn.dev v2 schema: { "name": "...", "downloadUrl": [ {"quality": "320kbps", "url": "..."} ], "image": [ {"quality": "500x500", "url": "..."} ] }
            val name = obj.get("name")?.asString ?: obj.get("title")?.asString ?: obj.get("song")?.asString ?: continue
            val id = obj.get("id")?.asString ?: obj.get("songid")?.asString ?: UUID.randomUUID().toString()

            // Extract best stream URL
            var streamUrl = ""
            if (obj.has("downloadUrl") && obj.get("downloadUrl").isJsonArray) {
                val downloads = obj.getAsJsonArray("downloadUrl")
                streamUrl = downloads.lastOrNull()?.asJsonObject?.get("url")?.asString
                    ?: downloads.firstOrNull()?.asJsonObject?.get("url")?.asString
                    ?: ""
            } else if (obj.has("url")) {
                streamUrl = obj.get("url").asString
            } else if (obj.has("media_url")) {
                streamUrl = obj.get("media_url").asString
            }

            // Extract best image
            var artUrl = ""
            if (obj.has("image") && obj.get("image").isJsonArray) {
                val images = obj.getAsJsonArray("image")
                artUrl = images.lastOrNull()?.asJsonObject?.get("url")?.asString
                    ?: images.firstOrNull()?.asJsonObject?.get("url")?.asString
                    ?: ""
            } else if (obj.has("image_url")) {
                artUrl = obj.get("image_url").asString
            } else if (obj.has("image") && obj.get("image").isJsonPrimitive) {
                artUrl = obj.get("image").asString
            }
            artUrl = artUrl.replace("150x150", "500x500")

            // Artist name
            var artistName = "Unknown Artist"
            if (obj.has("artists") && obj.get("artists").isJsonObject) {
                val artistsObj = obj.getAsJsonObject("artists")
                if (artistsObj.has("primary") && artistsObj.get("primary").isJsonArray) {
                    val primaries = artistsObj.getAsJsonArray("primary")
                    artistName = primaries.mapNotNull { it.asJsonObject.get("name")?.asString }.joinToString(", ")
                }
            } else if (obj.has("singers")) {
                artistName = obj.get("singers").asString
            } else if (obj.has("primary_artists")) {
                artistName = obj.get("primary_artists").asString
            }

            val albumName = if (obj.has("album") && obj.get("album").isJsonObject) {
                obj.getAsJsonObject("album").get("name")?.asString ?: name
            } else obj.get("album")?.asString ?: name

            val durationSec = obj.get("duration")?.asString?.toIntOrNull() ?: 180
            val artistObj = Artist(id = "saavn_artist_${artistName.hashCode()}", name = artistName, imageUrl = artUrl)
            val albumObj = Album(id = "saavn_album_${albumName.hashCode()}", name = albumName, artist = artistObj, artworkUrl = artUrl)

            list.add(
                Track(
                    id = "saavn_$id",
                    title = name,
                    artist = artistObj,
                    album = albumObj,
                    durationMs = durationSec * 1000L,
                    streamUrl = JioSaavnDecryptor.formatStreamUrl(streamUrl),
                    artworkUrl = artUrl,
                    genre = obj.get("language")?.asString?.replaceFirstChar { it.uppercase() } ?: "Pop",
                    playCount = 0L
                )
            )
        }
        return list
    }

    private fun JioSaavnSongDto.toTrack(): Track? {
        val songTitle = title ?: song ?: return null
        val songMediaUrl = mediaUrl ?: directMediaUrl ?: ""
        val trackId = songId ?: id ?: UUID.randomUUID().toString()
        val artistName = singers ?: primaryArtists ?: artist ?: "Unknown Artist"
        val albumName = album ?: songTitle
        val artUrl = (imageUrl ?: image)?.replace("150x150", "500x500")?.replace("http://", "https://")

        val durationSec = duration?.toIntOrNull() ?: 180
        val durationMs = durationSec * 1000L

        val artistObj = Artist(
            id = "saavn_artist_${artistName.hashCode()}",
            name = artistName,
            imageUrl = artUrl
        )

        val albumObj = Album(
            id = "saavn_album_${albumName.hashCode()}",
            name = albumName,
            artist = artistObj,
            artworkUrl = artUrl,
            releaseDate = year,
            trackCount = 1
        )

        return Track(
            id = "saavn_$trackId",
            title = songTitle,
            artist = artistObj,
            album = albumObj,
            durationMs = durationMs,
            streamUrl = JioSaavnDecryptor.formatStreamUrl(songMediaUrl),
            artworkUrl = artUrl,
            genre = language?.replaceFirstChar { it.uppercase() } ?: "Pop",
            playCount = 0L
        )
    }
}
