package com.musync.app.domain.repository

import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
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
    suspend fun getRecommendations(trackId: String, limit: Int = 5): Result<List<Track>>
    suspend fun getDiscoverTrending(region: String = "global", language: String = "All"): Result<List<Track>>
    suspend fun getDiscoverNew(language: String = "All"): Result<List<Track>>
    suspend fun getDiscoverRising(): Result<List<Track>>
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

interface DownloadRepository {
    fun getDownloadedTracks(): Flow<List<Track>>
    fun getCompletedCount(): Flow<Int>
    fun getTotalStorageUsed(): Flow<Long?>
    suspend fun isDownloaded(trackId: String): Boolean
    suspend fun downloadTrack(track: Track)
    suspend fun deleteDownload(trackId: String)
    suspend fun clearAllDownloads()
}


