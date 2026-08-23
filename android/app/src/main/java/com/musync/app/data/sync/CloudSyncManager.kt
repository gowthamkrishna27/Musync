package com.musync.app.data.sync

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.musync.app.auth.AuthManager
import com.musync.app.auth.CloudSyncStatus
import com.musync.app.data.local.database.MusyncDatabase
import com.musync.app.data.local.database.entity.FavoriteEntity
import com.musync.app.data.local.database.entity.PlaylistEntity
import com.musync.app.data.local.database.entity.PlaylistItemEntity
import com.musync.app.data.local.database.entity.RecentlyPlayedEntity
import com.musync.app.data.local.database.entity.UserEntity
import com.musync.app.domain.model.Track
import kotlinx.coroutines.flow.first

// ─────────────────────────────────────────────────────────────────────────────
// Supabase table row shapes (used by PostgREST upsert / select / delete).
// Column names match the Supabase Postgres schema exactly.
// See supabase_schema.sql in the artifacts directory for the CREATE TABLE DDL.
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
private data class UserRow(
    val uid: String,
    @SerialName("display_name") val displayName: String?,
    val email: String?,
    @SerialName("photo_url") val photoUrl: String?,
    val provider: String,
    @SerialName("is_anonymous") val isAnonymous: Boolean,
    @SerialName("last_login_at") val lastLoginAt: Long
)

@Serializable
private data class FavoriteRow(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("artist_handle") val artistHandle: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    val genre: String? = null,
    @SerialName("added_at") val addedAt: Long = System.currentTimeMillis()
)

@Serializable
private data class PlaylistRow(
    @SerialName("user_id") val userId: String,
    @SerialName("playlist_id") val playlistId: Long,
    val name: String,
    val description: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerialName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
private data class PlaylistTrackRow(
    @SerialName("playlist_id") val playlistId: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("artist_handle") val artistHandle: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    val genre: String? = null,
    val position: Int = 0,
    @SerialName("added_at") val addedAt: Long = System.currentTimeMillis()
)

@Serializable
private data class RecentlyPlayedRow(
    @SerialName("user_id") val userId: String,
    @SerialName("track_id") val trackId: String,
    val title: String,
    @SerialName("artist_id") val artistId: String = "",
    @SerialName("artist_name") val artistName: String = "",
    @SerialName("artist_handle") val artistHandle: String? = null,
    @SerialName("artwork_url") val artworkUrl: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    val genre: String? = null,
    @SerialName("played_at") val playedAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────────────────────────────────────

class CloudSyncManager(
    private val authManager: AuthManager,
    private val database: MusyncDatabase,
    private val supabase: SupabaseClient
) {
    companion object {
        private const val TAG = "CloudSyncManager"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    init {
        // Observe auth changes to trigger sync when a non-anonymous user logs in
        scope.launch {
            authManager.currentUser.collect { user ->
                if (user != null && !user.isAnonymous) {
                    Log.d(TAG, "User logged in: ${user.uid} (${user.provider}). Starting full sync...")
                    triggerFullSync(user.uid)
                } else {
                    Log.d(TAG, "User logged out or guest. Skipping cloud sync.")
                }
            }
        }
    }

    fun triggerSync() {
        val user = authManager.currentUser.value
        if (user != null && !user.isAnonymous) {
            triggerFullSync(user.uid)
        }
    }

    private fun triggerFullSync(userId: String) {
        syncJob?.cancel()
        syncJob = scope.launch {
            authManager.setSyncStatus(CloudSyncStatus.SYNCING)
            try {
                syncUserProfile(userId)
                syncFavorites(userId)
                syncPlaylists(userId)
                syncRecentlyPlayed(userId)
                authManager.setSyncStatus(CloudSyncStatus.SYNCED)
                Log.d(TAG, "Cloud sync complete for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Cloud sync failed for user $userId", e)
                authManager.setSyncStatus(CloudSyncStatus.ERROR)
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 1. TWO-WAY MERGE ON INITIAL LOGIN
    // ──────────────────────────────────────────────────────────────

    private suspend fun syncUserProfile(userId: String) {
        val user = authManager.currentUser.value ?: return

        // Write to local Room DB
        database.userDao().insertUser(
            UserEntity(
                uid = user.uid,
                displayName = user.displayName,
                email = user.email,
                photoUrl = user.photoUrl,
                provider = user.provider.name,
                isAnonymous = user.isAnonymous,
                lastLoginAt = System.currentTimeMillis()
            )
        )

        // Upsert to Supabase users table
        supabase.postgrest.from("users").upsert(
            UserRow(
                uid = user.uid,
                displayName = user.displayName ?: "Musync User",
                email = user.email ?: "",
                photoUrl = user.photoUrl ?: "",
                provider = user.provider.name,
                isAnonymous = user.isAnonymous,
                lastLoginAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun syncFavorites(userId: String) {
        // 1. Fetch remote favorites
        val remoteRows = supabase.postgrest.from("favorites")
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
            }
            .decodeList<FavoriteRow>()

        val remoteMap = remoteRows.associateBy { it.trackId }

        // 2. Fetch local favorites
        val localFavorites = database.favoritesDao().getAllFavorites().first()
        val localMap = localFavorites.associateBy { it.trackId }

        // 3. Merge remote into local Room DB
        val toInsertLocally = remoteMap.values
            .filter { !localMap.containsKey(it.trackId) }
            .map { row ->
                FavoriteEntity(
                    trackId = row.trackId,
                    title = row.title,
                    artistId = row.artistId,
                    artistName = row.artistName,
                    artistHandle = row.artistHandle,
                    artworkUrl = row.artworkUrl,
                    durationMs = row.durationMs,
                    streamUrl = row.streamUrl,
                    genre = row.genre,
                    addedAt = row.addedAt
                )
            }
        if (toInsertLocally.isNotEmpty()) {
            database.favoritesDao().insertFavorites(toInsertLocally)
        }

        // 4. Push local items missing from cloud
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.trackId) }
        for (localFav in toPushRemotely) {
            try {
                supabase.postgrest.from("favorites").upsert(
                    FavoriteRow(
                        userId = userId,
                        trackId = localFav.trackId,
                        title = localFav.title,
                        artistId = localFav.artistId,
                        artistName = localFav.artistName,
                        artistHandle = localFav.artistHandle,
                        artworkUrl = localFav.artworkUrl,
                        durationMs = localFav.durationMs,
                        streamUrl = localFav.streamUrl,
                        genre = localFav.genre,
                        addedAt = localFav.addedAt
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing favorite ${localFav.trackId}: ${e.message}")
            }
        }
    }

    private suspend fun syncPlaylists(userId: String) {
        // 1. Fetch remote playlists
        val remoteRows = supabase.postgrest.from("playlists")
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
            }
            .decodeList<PlaylistRow>()

        val remoteMap = remoteRows.associateBy { it.playlistId }

        // 2. Fetch local playlists
        val localPlaylists = database.playlistDao().getAllPlaylists().first()
        val localMap = localPlaylists.associateBy { it.id }

        // 3. Merge remote playlists into local Room DB
        val toInsertLocally = remoteMap.values.filter { !localMap.containsKey(it.playlistId) }
            .map { row ->
                PlaylistEntity(
                    id = row.playlistId,
                    name = row.name,
                    description = row.description,
                    artworkUrl = row.artworkUrl,
                    createdAt = row.createdAt
                )
            }
        if (toInsertLocally.isNotEmpty()) {
            database.playlistDao().insertPlaylists(toInsertLocally)
        }

        // 4. For every remote playlist, sync its track rows
        for (playlistId in remoteMap.keys) {
            fetchAndMergePlaylistTracks(userId, playlistId)
        }

        // 5. Push local playlists missing from cloud
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.id) }
        for (pl in toPushRemotely) {
            try {
                supabase.postgrest.from("playlists").upsert(
                    PlaylistRow(
                        userId = userId,
                        playlistId = pl.id,
                        name = pl.name,
                        description = pl.description,
                        artworkUrl = pl.artworkUrl,
                        createdAt = pl.createdAt
                    )
                )
                // Push its tracks
                val tracks = database.playlistDao().getPlaylistTracksSync(pl.id)
                for (track in tracks) {
                    try {
                        supabase.postgrest.from("playlist_tracks").upsert(
                            PlaylistTrackRow(
                                playlistId = pl.id,
                                userId = userId,
                                trackId = track.trackId,
                                title = track.title,
                                artistId = track.artistId,
                                artistName = track.artistName,
                                artistHandle = track.artistHandle,
                                artworkUrl = track.artworkUrl,
                                durationMs = track.durationMs,
                                streamUrl = track.streamUrl,
                                genre = track.genre,
                                position = track.position,
                                addedAt = track.addedAt
                            )
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed pushing track ${track.trackId} for playlist ${pl.id}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing playlist ${pl.id}: ${e.message}")
            }
        }
    }

    private suspend fun fetchAndMergePlaylistTracks(userId: String, playlistId: Long) {
        try {
            val remoteItems = supabase.postgrest.from("playlist_tracks")
                .select(Columns.ALL) {
                    filter {
                        eq("user_id", userId)
                        eq("playlist_id", playlistId)
                    }
                }
                .decodeList<PlaylistTrackRow>()

            if (remoteItems.isNotEmpty()) {
                database.playlistDao().insertPlaylistItems(
                    remoteItems.map { row ->
                        PlaylistItemEntity(
                            playlistId = playlistId,
                            trackId = row.trackId,
                            title = row.title,
                            artistId = row.artistId,
                            artistName = row.artistName,
                            artistHandle = row.artistHandle,
                            artworkUrl = row.artworkUrl,
                            durationMs = row.durationMs,
                            streamUrl = row.streamUrl,
                            genre = row.genre,
                            position = row.position,
                            addedAt = row.addedAt
                        )
                    }
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch tracks for playlist $playlistId: ${e.message}")
        }
    }

    private suspend fun syncRecentlyPlayed(userId: String) {
        // 1. Fetch remote history
        val remoteRows = supabase.postgrest.from("recently_played")
            .select(Columns.ALL) {
                filter { eq("user_id", userId) }
            }
            .decodeList<RecentlyPlayedRow>()

        val remoteMap = remoteRows.associateBy { it.trackId }

        // 2. Fetch local history
        val localHistory = database.recentlyPlayedDao().getRecentlyPlayed(50).first()
        val localMap = localHistory.associateBy { it.trackId }

        // 3. Merge remote into local Room DB
        val toInsertLocally = remoteMap.values.filter { !localMap.containsKey(it.trackId) }
            .map { row ->
                RecentlyPlayedEntity(
                    trackId = row.trackId,
                    title = row.title,
                    artistId = row.artistId,
                    artistName = row.artistName,
                    artistHandle = row.artistHandle,
                    artworkUrl = row.artworkUrl,
                    durationMs = row.durationMs,
                    streamUrl = row.streamUrl,
                    genre = row.genre,
                    playedAt = row.playedAt
                )
            }
        if (toInsertLocally.isNotEmpty()) {
            database.recentlyPlayedDao().insertRecentlyPlayedList(toInsertLocally)
        }

        // 4. Push local items missing from cloud
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.trackId) }
        for (item in toPushRemotely) {
            try {
                supabase.postgrest.from("recently_played").upsert(
                    RecentlyPlayedRow(
                        userId = userId,
                        trackId = item.trackId,
                        title = item.title,
                        artistId = item.artistId,
                        artistName = item.artistName,
                        artistHandle = item.artistHandle,
                        artworkUrl = item.artworkUrl,
                        durationMs = item.durationMs,
                        streamUrl = item.streamUrl,
                        genre = item.genre,
                        playedAt = item.playedAt
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed pushing history ${item.trackId}: ${e.message}")
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 2. REAL-TIME ITEM MUTATION HOOKS (invoked by Repositories)
    // ──────────────────────────────────────────────────────────────

    fun syncSingleFavorite(track: Track, isFavorite: Boolean) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                if (isFavorite) {
                    supabase.postgrest.from("favorites").upsert(
                        FavoriteRow(
                            userId = user.uid,
                            trackId = track.id,
                            title = track.title,
                            artistId = track.artist.id,
                            artistName = track.artist.name,
                            artistHandle = track.artist.handle,
                            artworkUrl = track.artworkUrl,
                            durationMs = track.durationMs,
                            streamUrl = track.streamUrl,
                            genre = track.genre,
                            addedAt = System.currentTimeMillis()
                        )
                    )
                    Log.d(TAG, "Synced favorite added: ${track.id}")
                } else {
                    supabase.postgrest.from("favorites").delete {
                        filter {
                            eq("user_id", user.uid)
                            eq("track_id", track.id)
                        }
                    }
                    Log.d(TAG, "Synced favorite removed: ${track.id}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing favorite for ${track.id}: ${e.message}")
            }
        }
    }

    fun syncCreateOrUpdatePlaylist(playlistId: Long, name: String, description: String? = null, artworkUrl: String? = null) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                supabase.postgrest.from("playlists").upsert(
                    PlaylistRow(
                        userId = user.uid,
                        playlistId = playlistId,
                        name = name,
                        description = description,
                        artworkUrl = artworkUrl,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Synced playlist: $playlistId ($name)")
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing playlist $playlistId: ${e.message}")
            }
        }
    }

    fun syncDeletePlaylist(playlistId: Long) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                // Delete playlist tracks first (cascades if FK + ON DELETE CASCADE is set)
                supabase.postgrest.from("playlist_tracks").delete {
                    filter {
                        eq("user_id", user.uid)
                        eq("playlist_id", playlistId)
                    }
                }
                // Then delete the playlist itself
                supabase.postgrest.from("playlists").delete {
                    filter {
                        eq("user_id", user.uid)
                        eq("playlist_id", playlistId)
                    }
                }
                Log.d(TAG, "Synced playlist deleted: $playlistId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed deleting playlist $playlistId in cloud: ${e.message}")
            }
        }
    }

    fun syncAddTrackToPlaylist(playlistId: Long, track: Track, position: Int) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                supabase.postgrest.from("playlist_tracks").upsert(
                    PlaylistTrackRow(
                        playlistId = playlistId,
                        userId = user.uid,
                        trackId = track.id,
                        title = track.title,
                        artistId = track.artist.id,
                        artistName = track.artist.name,
                        artistHandle = track.artist.handle,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        streamUrl = track.streamUrl,
                        genre = track.genre,
                        position = position,
                        addedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Synced track added to playlist $playlistId: ${track.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed adding track ${track.id} to playlist $playlistId in cloud: ${e.message}")
            }
        }
    }

    fun syncRemoveTrackFromPlaylist(playlistId: Long, trackId: String) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                supabase.postgrest.from("playlist_tracks").delete {
                    filter {
                        eq("user_id", user.uid)
                        eq("playlist_id", playlistId)
                        eq("track_id", trackId)
                    }
                }
                Log.d(TAG, "Synced track removed from playlist $playlistId: $trackId")
            } catch (e: Exception) {
                Log.w(TAG, "Failed removing track $trackId from playlist $playlistId in cloud: ${e.message}")
            }
        }
    }

    fun syncRecordPlayed(track: Track) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                supabase.postgrest.from("recently_played").upsert(
                    RecentlyPlayedRow(
                        userId = user.uid,
                        trackId = track.id,
                        title = track.title,
                        artistId = track.artist.id,
                        artistName = track.artist.name,
                        artistHandle = track.artist.handle,
                        artworkUrl = track.artworkUrl,
                        durationMs = track.durationMs,
                        streamUrl = track.streamUrl,
                        genre = track.genre,
                        playedAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Synced history track: ${track.id}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed recording history for ${track.id} in cloud: ${e.message}")
            }
        }
    }

    fun syncClearHistory() {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                supabase.postgrest.from("recently_played").delete {
                    filter { eq("user_id", user.uid) }
                }
                Log.d(TAG, "Synced history cleared in cloud")
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing history in cloud: ${e.message}")
            }
        }
    }
}
