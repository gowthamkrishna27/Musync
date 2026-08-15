package com.missingcore.music.data.api

import com.missingcore.music.domain.model.Album
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.provider.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Universal Multi-Catalog Music Provider.
 * 
 * Aggregates:
 * 1. YouTube Music & ytmusicapi
 * 2. JioSaavn (with DES audio stream decryption)
 * 3. Custom user endpoints
 */
class UniversalMusicProvider(
    private val ytMusicProvider: YouTubeMusicProvider = YouTubeMusicProvider(),
    private val saavnProvider: JioSaavnMusicProvider = JioSaavnMusicProvider()
) : MusicProvider {

    override val providerId: String = "universal"
    override val displayName: String = "Universal Music Engine"

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        ytMusicProvider.updateConfiguration(baseUrl, apiKey)
        saavnProvider.updateConfiguration(baseUrl, apiKey)
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean {
        return ytMusicProvider.testConnection(baseUrl, apiKey) || saavnProvider.testConnection(baseUrl, apiKey)
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val saavnDeferred = async { saavnProvider.search(query) }
        val ytDeferred = async { ytMusicProvider.search(query) }

        val saavnResults = try { saavnDeferred.await() } catch (_: Exception) { emptyList() }
        val ytResults = try { ytDeferred.await() } catch (_: Exception) { emptyList() }

        val combined = mutableListOf<Track>()
        // Interleave or combine results with distinct titles
        combined.addAll(saavnResults)
        combined.addAll(ytResults)
        combined.distinctBy { it.title.lowercase().trim() }
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        search(query).map { it.artist }.distinctBy { it.name }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        emptyList()
    }

    override suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        val saavn = try { saavnProvider.getTrending() } catch (_: Exception) { emptyList() }
        if (saavn.isNotEmpty()) return@withContext saavn
        ytMusicProvider.getTrending()
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        val yt = try { ytMusicProvider.getUndergroundTrending() } catch (_: Exception) { emptyList() }
        if (yt.isNotEmpty()) return@withContext yt
        saavnProvider.getUndergroundTrending()
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        if (id.startsWith("saavn_")) {
            saavnProvider.getTrack(id)
        } else {
            ytMusicProvider.getTrack(id)
        }
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        if (id.startsWith("saavn_")) {
            saavnProvider.getArtist(id)
        } else {
            ytMusicProvider.getArtist(id)
        }
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        search(artistId.removePrefix("saavn_artist_").removePrefix("yt_artist_"))
    }

    override suspend fun getAlbum(id: String): Album? = withContext(Dispatchers.IO) {
        if (id.startsWith("saavn_")) {
            saavnProvider.getAlbum(id)
        } else {
            ytMusicProvider.getAlbum(id)
        }
    }

    override suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        saavnProvider.getPlaylist(id) ?: ytMusicProvider.getPlaylist(id)
    }

    override suspend fun getStreamUrl(track: Track): String? {
        return if (!track.streamUrl.isNullOrBlank()) {
            track.streamUrl
        } else if (track.id.startsWith("saavn_")) {
            saavnProvider.getStreamUrl(track)
        } else {
            ytMusicProvider.getStreamUrl(track)
        }
    }
}
