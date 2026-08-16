package com.musync.app.data.remote

import com.musync.app.data.remote.dto.AudiusArtworkDto
import com.musync.app.data.remote.dto.AudiusPlaylistDto
import com.musync.app.data.remote.dto.AudiusTrackDto
import com.musync.app.data.remote.dto.AudiusUserDto
import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.provider.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class AudiusMusicProvider(
    private var currentBaseUrl: String = DEFAULT_BASE_URL,
    private var currentApiKey: String? = null
) : MusicProvider {

    override val providerId: String = "audius"
    override val displayName: String = "Audius Music"

    private var apiService: AudiusApiService = createService(currentBaseUrl, currentApiKey)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.audius.co/v1/"
    }

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        val newBaseUrl = if (!baseUrl.isNullOrBlank()) {
            if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        } else {
            DEFAULT_BASE_URL
        }
        currentBaseUrl = newBaseUrl
        currentApiKey = apiKey
        apiService = createService(newBaseUrl, apiKey)
    }

    private fun createService(baseUrl: String, apiKey: String?): AudiusApiService {
        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()
                    .header("User-Agent", "Musync-Android/1.0")
                    .header("Accept", "application/json")
                if (!apiKey.isNullOrBlank()) {
                    requestBuilder.header("x-api-key", apiKey)
                }
                chain.proceed(requestBuilder.build())
            }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        clientBuilder.addInterceptor(logging)

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(clientBuilder.build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AudiusApiService::class.java)
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val testService = createService(
                baseUrl = if (!baseUrl.isNullOrBlank()) {
                    if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                } else DEFAULT_BASE_URL,
                apiKey = apiKey
            )
            val response = testService.getTrendingTracks(limit = 1)
            response.isSuccessful && response.body()?.data != null
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchTracks(query = query)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain(currentBaseUrl) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchUsers(query = query)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain() } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchPlaylists(query = query)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain(currentBaseUrl) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrendingTracks(limit = 30)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain(currentBaseUrl) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUndergroundTrending(limit = 30)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain(currentBaseUrl) } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getTrack(id)
            if (response.isSuccessful) {
                response.body()?.data?.toDomain(currentBaseUrl)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUser(id)
            if (response.isSuccessful) {
                response.body()?.data?.toDomain()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getUserTracks(artistId)
            if (response.isSuccessful) {
                response.body()?.data?.mapNotNull { it.toDomain(currentBaseUrl) } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getAlbum(id: String): Album? = null

    override suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getPlaylist(id)
            if (response.isSuccessful) {
                response.body()?.data?.firstOrNull()?.toDomain(currentBaseUrl)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun getStreamUrl(track: Track): String? {
        val cleanBase = if (currentBaseUrl.endsWith("/")) currentBaseUrl else "$currentBaseUrl/"
        return "${cleanBase}tracks/${track.id}/stream?app_name=musync"
    }

    private fun AudiusTrackDto.toDomain(baseUrl: String): Track? {
        val trackTitle = title?.takeIf { it.isNotBlank() } ?: return null
        val artistName = user?.name?.takeIf { it.isNotBlank() } ?: user?.handle ?: "Unknown Artist"
        val artUrl = artwork?.extractBestUrl() ?: user?.profilePicture?.extractBestUrl()
        val durationInMs = duration?.let { it * 1000L }
        val streamCleanBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val stream = "${streamCleanBase}tracks/$id/stream?app_name=musync"

        return Track(
            id = id,
            title = trackTitle,
            artist = Artist(
                id = user?.id ?: "unknown",
                name = artistName,
                handle = user?.handle,
                imageUrl = user?.profilePicture?.extractBestUrl()
            ),
            album = null,
            artworkUrl = artUrl,
            durationMs = durationInMs,
            streamUrl = stream,
            genre = genre,
            playCount = playCount,
            explicit = false
        )
    }

    private fun AudiusUserDto.toDomain(): Artist? {
        val artistName = name?.takeIf { it.isNotBlank() } ?: handle ?: return null
        return Artist(
            id = id,
            name = artistName,
            handle = handle,
            imageUrl = profilePicture?.extractBestUrl(),
            bio = bio,
            trackCount = trackCount
        )
    }

    private fun AudiusPlaylistDto.toDomain(baseUrl: String): Playlist? {
        val pName = playlistName?.takeIf { it.isNotBlank() } ?: return null
        val domainTracks = tracks?.mapNotNull { it.toDomain(baseUrl) } ?: emptyList()
        return Playlist(
            id = id,
            name = pName,
            description = description,
            artworkUrl = artwork?.extractBestUrl() ?: user?.profilePicture?.extractBestUrl(),
            tracks = domainTracks,
            isCustom = false
        )
    }

    private fun AudiusArtworkDto.extractBestUrl(): String? {
        return large ?: medium ?: small
    }
}

