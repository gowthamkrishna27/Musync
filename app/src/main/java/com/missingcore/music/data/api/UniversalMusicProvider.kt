package com.missingcore.music.data.api

import com.missingcore.music.domain.model.Album
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import com.missingcore.music.domain.provider.MusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal YouTube Music Provider.
 * 
 * Powered by ytmusicapi (https://musync-ytmusic-api.onrender.com) and high-speed YouTube streaming.
 */
class UniversalMusicProvider(
    private val ytMusicProvider: YouTubeMusicProvider = YouTubeMusicProvider()
) : MusicProvider {

    override val providerId: String = "ytmusic"
    override val displayName: String = "YouTube Music"

    fun updateConfiguration(baseUrl: String?, apiKey: String?) {
        ytMusicProvider.updateConfiguration(baseUrl, apiKey)
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean {
        return ytMusicProvider.testConnection(baseUrl, apiKey)
    }

    override suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.search(query)
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        ytMusicProvider.searchArtists(query)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> = withContext(Dispatchers.IO) {
        ytMusicProvider.searchPlaylists(query)
    }

    override suspend fun getTrending(): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getTrending()
    }

    override suspend fun getUndergroundTrending(): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getUndergroundTrending()
    }

    override suspend fun getTrack(id: String): Track? = withContext(Dispatchers.IO) {
        ytMusicProvider.getTrack(id)
    }

    override suspend fun getArtist(id: String): Artist? = withContext(Dispatchers.IO) {
        ytMusicProvider.getArtist(id)
    }

    override suspend fun getArtistTracks(artistId: String): List<Track> = withContext(Dispatchers.IO) {
        ytMusicProvider.getArtistTracks(artistId)
    }

    override suspend fun getAlbum(id: String): Album? = withContext(Dispatchers.IO) {
        ytMusicProvider.getAlbum(id)
    }

    override suspend fun getPlaylist(id: String): Playlist? = withContext(Dispatchers.IO) {
        ytMusicProvider.getPlaylist(id)
    }

    override suspend fun getStreamUrl(track: Track): String? {
        return ytMusicProvider.getStreamUrl(track)
    }
}
