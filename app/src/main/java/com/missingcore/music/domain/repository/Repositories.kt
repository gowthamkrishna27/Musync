package com.missingcore.music.domain.repository

import com.missingcore.music.domain.model.Album
import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.Playlist
import com.missingcore.music.domain.model.Track
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    suspend fun getTrending(): Result<List<Track>>
    suspend fun getUndergroundTrending(): Result<List<Track>>
    suspend fun search(query: String): Result<List<Track>>
    suspend fun searchArtists(query: String): Result<List<Artist>>
    suspend fun searchPlaylists(query: String): Result<List<Playlist>>
    suspend fun getTrack(id: String): Result<Track?>
    suspend fun getArtist(id: String): Result<Artist?>
    suspend fun getArtistTracks(artistId: String): Result<List<Track>>
    suspend fun getPlaylist(id: String): Result<Playlist?>
    suspend fun getStreamUrl(track: Track): String?
    suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean
}

interface FavoritesRepository {
    fun getFavorites(): Flow<List<Track>>
    fun isFavorite(trackId: String): Flow<Boolean>
    suspend fun toggleFavorite(track: Track)
    suspend fun removeFavorite(trackId: String)
}

interface PlaylistRepository {
    fun getPlaylists(): Flow<List<Playlist>>
    fun getPlaylistWithTracks(playlistId: String): Flow<Playlist?>
    suspend fun createPlaylist(name: String, description: String? = null): Long
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTrackToPlaylist(playlistId: String, track: Track)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
}

interface RecentlyPlayedRepository {
    fun getRecentlyPlayed(limit: Int = 50): Flow<List<Track>>
    suspend fun recordPlayed(track: Track)
    suspend fun clearHistory()
}
