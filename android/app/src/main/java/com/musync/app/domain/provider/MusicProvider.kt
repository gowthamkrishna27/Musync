package com.musync.app.domain.provider

import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track

interface MusicProvider {
    val providerId: String
    val displayName: String

    suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean

    suspend fun search(query: String): List<Track>

    suspend fun searchArtists(query: String): List<Artist>

    suspend fun searchPlaylists(query: String): List<Playlist>

    suspend fun getTrending(): List<Track>

    suspend fun getUndergroundTrending(): List<Track>

    suspend fun getTrack(id: String): Track?

    suspend fun getArtist(id: String): Artist?

    suspend fun getArtistTracks(artistId: String): List<Track>

    suspend fun getAlbum(id: String): Album?

    suspend fun getPlaylist(id: String): Playlist?

    suspend fun getStreamUrl(track: Track): String?
    suspend fun getRecommendations(trackId: String, limit: Int = 5): List<Track> = emptyList()
}

