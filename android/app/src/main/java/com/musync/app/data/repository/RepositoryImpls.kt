package com.musync.app.data.repository

import com.musync.app.data.local.database.dao.FavoritesDao
import com.musync.app.data.local.database.dao.PlaylistDao
import com.musync.app.data.local.database.dao.RecentlyPlayedDao
import com.musync.app.data.local.database.dao.TrackCacheDao
import com.musync.app.data.local.database.entity.CachedTrackEntity
import com.musync.app.data.local.database.entity.FavoriteEntity
import com.musync.app.data.local.database.entity.PlaylistEntity
import com.musync.app.data.local.database.entity.PlaylistItemEntity
import com.musync.app.data.local.database.entity.RecentlyPlayedEntity
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.provider.MusicProvider
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MusicRepositoryImpl(
    private var provider: MusicProvider,
    private val trackCacheDao: TrackCacheDao
) : MusicRepository {

    fun setProvider(newProvider: MusicProvider) {
        provider = newProvider
    }

    override suspend fun getTrending(): Result<List<Track>> = runCatching {
        val tracks = provider.getTrending()
        if (tracks.isNotEmpty()) {
            trackCacheDao.insertCachedTracks(tracks.map { CachedTrackEntity.fromTrack(it) })
        }
        tracks
    }

    override suspend fun getUndergroundTrending(): Result<List<Track>> = runCatching {
        val tracks = provider.getUndergroundTrending()
        if (tracks.isNotEmpty()) {
            trackCacheDao.insertCachedTracks(tracks.map { CachedTrackEntity.fromTrack(it) })
        }
        tracks
    }

    override suspend fun search(query: String): Result<List<Track>> = runCatching {
        val tracks = provider.search(query)
        if (tracks.isNotEmpty()) {
            trackCacheDao.insertCachedTracks(tracks.map { CachedTrackEntity.fromTrack(it) })
        }
        tracks
    }

    override suspend fun searchArtists(query: String): Result<List<Artist>> = runCatching {
        provider.searchArtists(query)
    }

    override suspend fun searchPlaylists(query: String): Result<List<Playlist>> = runCatching {
        provider.searchPlaylists(query)
    }

    override suspend fun getTrack(id: String): Result<Track?> = runCatching {
        val remote = provider.getTrack(id)
        if (remote != null) {
            trackCacheDao.insertCachedTrack(CachedTrackEntity.fromTrack(remote))
            remote
        } else {
            trackCacheDao.getCachedTrack(id)?.toTrack()
        }
    }

    override suspend fun getArtist(id: String): Result<Artist?> = runCatching {
        provider.getArtist(id)
    }

    override suspend fun getArtistTracks(artistId: String): Result<List<Track>> = runCatching {
        provider.getArtistTracks(artistId)
    }

    override suspend fun getPlaylist(id: String): Result<Playlist?> = runCatching {
        provider.getPlaylist(id)
    }

    override suspend fun getStreamUrl(track: Track): String? {
        return provider.getStreamUrl(track)
    }

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean {
        return provider.testConnection(baseUrl, apiKey)
    }

    override suspend fun getRecommendations(trackId: String, limit: Int): Result<List<Track>> = runCatching {
        val tracks = provider.getRecommendations(trackId, limit)
        if (tracks.isNotEmpty()) {
            trackCacheDao.insertCachedTracks(tracks.map { CachedTrackEntity.fromTrack(it) })
        }
        tracks
    }
}

class FavoritesRepositoryImpl(
    private val favoritesDao: FavoritesDao,
    private val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager? = null
) : FavoritesRepository {

    override fun getFavorites(): Flow<List<Track>> {
        return favoritesDao.getAllFavorites().map { list -> list.map { it.toTrack() } }
    }

    override fun isFavorite(trackId: String): Flow<Boolean> {
        return favoritesDao.isFavorite(trackId)
    }

    override suspend fun toggleFavorite(track: Track) {
        val exists = favoritesDao.isFavoriteSync(track.id)
        if (exists) {
            favoritesDao.deleteFavorite(track.id)
            cloudSyncManager?.syncSingleFavorite(track, isFavorite = false)
        } else {
            favoritesDao.insertFavorite(FavoriteEntity.fromTrack(track))
            cloudSyncManager?.syncSingleFavorite(track, isFavorite = true)
        }
    }

    override suspend fun removeFavorite(trackId: String) {
        favoritesDao.deleteFavorite(trackId)
        val dummyTrack = Track(id = trackId, title = "", artist = Artist(id = "", name = ""))
        cloudSyncManager?.syncSingleFavorite(dummyTrack, isFavorite = false)
    }
}

class PlaylistRepositoryImpl(
    private val playlistDao: PlaylistDao,
    private val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager? = null
) : PlaylistRepository {

    override fun getPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists().map { list ->
            list.map { entity ->
                Playlist(
                    id = entity.id.toString(),
                    name = entity.name,
                    description = entity.description,
                    artworkUrl = entity.artworkUrl,
                    isCustom = true,
                    createdAt = entity.createdAt
                )
            }
        }
    }

    override fun getPlaylistWithTracks(playlistId: String): Flow<Playlist?> {
        val idLong = playlistId.toLongOrNull() ?: return kotlinx.coroutines.flow.flowOf(null)
        return playlistDao.getPlaylistTracks(idLong).map { items ->
            val entity = playlistDao.getPlaylistById(idLong)
            entity?.let {
                Playlist(
                    id = it.id.toString(),
                    name = it.name,
                    description = it.description,
                    artworkUrl = items.firstOrNull()?.artworkUrl ?: it.artworkUrl,
                    tracks = items.map { item -> item.toTrack() },
                    isCustom = true,
                    createdAt = it.createdAt
                )
            }
        }
    }

    override suspend fun createPlaylist(name: String, description: String?): Long {
        val id = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name,
                description = description
            )
        )
        cloudSyncManager?.syncCreateOrUpdatePlaylist(id, name, description)
        return id
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        playlistId.toLongOrNull()?.let { id ->
            playlistDao.updatePlaylistName(id, newName)
            val entity = playlistDao.getPlaylistById(id)
            cloudSyncManager?.syncCreateOrUpdatePlaylist(id, newName, entity?.description, entity?.artworkUrl)
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistId.toLongOrNull()?.let { id ->
            playlistDao.deletePlaylist(id)
            cloudSyncManager?.syncDeletePlaylist(id)
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, track: Track) {
        val idLong = playlistId.toLongOrNull() ?: return
        val currentCount = playlistDao.getPlaylistTrackCount(idLong)
        playlistDao.insertPlaylistItem(
            PlaylistItemEntity.fromTrack(idLong, track, currentCount)
        )
        cloudSyncManager?.syncAddTrackToPlaylist(idLong, track, currentCount)
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        val idLong = playlistId.toLongOrNull() ?: return
        playlistDao.removeTrackFromPlaylist(idLong, trackId)
        cloudSyncManager?.syncRemoveTrackFromPlaylist(idLong, trackId)
    }
}

class RecentlyPlayedRepositoryImpl(
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager? = null
) : RecentlyPlayedRepository {

    override fun getRecentlyPlayed(limit: Int): Flow<List<Track>> {
        return recentlyPlayedDao.getRecentlyPlayed(limit).map { list -> list.map { it.toTrack() } }
    }

    override suspend fun recordPlayed(track: Track) {
        recentlyPlayedDao.insertRecentlyPlayed(RecentlyPlayedEntity.fromTrack(track))
        cloudSyncManager?.syncRecordPlayed(track)
    }

    override suspend fun clearHistory() {
        recentlyPlayedDao.clearAll()
        cloudSyncManager?.syncClearHistory()
    }
}

