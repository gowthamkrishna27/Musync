package com.musync.app.data.remote

import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.provider.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Universal Dual-Engine Music Provider.
 * 
 * Aggregates:
 * 1. YouTube Music (Metadata from Render ytmusicapi + In-App Stream Gateways)
 * 2. Audius Music Network (320kbps High-Bitrate Direct Edge CDN Streaming)
 */
class UniversalMusicProvider(
    private val ytMusicProvider: YouTubeMusicProvider = YouTubeMusicProvider(),
    private val audiusProvider: AudiusMusicProvider = AudiusMusicProvider()
) : MusicProvider {

    override val providerId: String = "universal"
    override val displayName: String = "Universal Music Engine"

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        ytMusicProvider.updateConfiguration(baseUrl, apiKey)
        if (baseUrl != null && baseUrl.contains("audius")) {
            audiusProvider.updateConfiguration(baseUrl, apiKey)
        }
    }

    fun updateAudioQuality(quality: String) {
        ytMusicProvider.updateAudioQuality(quality)
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean {
        return ytMusicProvider.testConnection(baseUrl, apiKey) || audiusProvider.testConnection(baseUrl, apiKey)
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val ytDeferred = async { ytMusicProvider.search(query) }
        val audiusDeferred = async { audiusProvider.search(query) }

        val ytResults = try { ytDeferred.await() } catch (_: Exception) { emptyList() }
        val audiusResults = try { audiusDeferred.await() } catch (_: Exception) { emptyList() }

        val combined = mutableListOf<Track>()
        combined.addAll(ytResults)
        combined.addAll(audiusResults)
        combined.distinctBy { it.title.lowercase().trim() }
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val yt = try { ytMusicProvider.searchArtists(query) } catch (_: Exception) { emptyList() }
        val audius = try { audiusProvider.searchArtists(query) } catch (_: Exception) { emptyList() }
        (yt + audius).distinctBy { it.name.lowercase().trim() }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        audiusProvider.searchPlaylists(query)
    }

    override suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        val yt = try { ytMusicProvider.getTrending() } catch (_: Exception) { emptyList() }
        if (yt.isNotEmpty()) return@withContext yt
        val audius = try { audiusProvider.getTrending() } catch (_: Exception) { emptyList() }
        audius.distinctBy { it.title.lowercase().trim() }
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        val yt = try { ytMusicProvider.getUndergroundTrending() } catch (_: Exception) { emptyList() }
        if (yt.isNotEmpty()) return@withContext yt
        val audius = try { audiusProvider.getUndergroundTrending() } catch (_: Exception) { emptyList() }
        audius.distinctBy { it.title.lowercase().trim() }
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        if (id.startsWith("yt_")) {
            ytMusicProvider.getTrack(id)
        } else {
            audiusProvider.getTrack(id) ?: ytMusicProvider.getTrack(id)
        }
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        audiusProvider.getArtist(id) ?: ytMusicProvider.getArtist(id)
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        search(artistId.removePrefix("yt_artist_").removePrefix("audius_"))
    }

    override suspend fun getAlbum(id: String): Album? = withContext(Dispatchers.IO) {
        ytMusicProvider.getAlbum(id)
    }

    override suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        audiusProvider.getPlaylist(id) ?: ytMusicProvider.getPlaylist(id)
    }

    override suspend fun getStreamUrl(track: Track): String? {
        if (!track.streamUrl.isNullOrBlank()) {
            return track.streamUrl
        }
        if (track.id.startsWith("yt_")) {
            return ytMusicProvider.getStreamUrl(track)
        }
        return audiusProvider.getStreamUrl(track) ?: ytMusicProvider.getStreamUrl(track)
    }

    override suspend fun getRecommendations(trackId: String, limit: Int): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getRecommendations(trackId, limit)
    }

    suspend fun getDiscoverTrending(region: String = "global", language: String = "All"): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getDiscoverTrending(region, language)
    }

    suspend fun getDiscoverNew(language: String = "All"): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getDiscoverNew(language)
    }

    suspend fun getDiscoverRising(): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getDiscoverRising()
    }
}

