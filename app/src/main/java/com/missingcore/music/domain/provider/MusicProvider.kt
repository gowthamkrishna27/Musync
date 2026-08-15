package com.missingcore.music.domain.provider

import com.missingcore.music.domain.model.Album
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track

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
}
