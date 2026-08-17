package com.musync.app.data.sync

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.musync.app.auth.AuthManager
import com.musync.app.auth.CloudSyncStatus
import com.musync.app.data.local.database.MusyncDatabase
import com.musync.app.data.local.database.entity.FavoriteEntity
import com.musync.app.data.local.database.entity.PlaylistEntity
import com.musync.app.data.local.database.entity.PlaylistItemEntity
import com.musync.app.data.local.database.entity.RecentlyPlayedEntity
import com.musync.app.data.local.database.entity.UserEntity
import com.musync.app.domain.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

class CloudSyncManager(
    private val authManager: AuthManager,
    private val database: MusyncDatabase
) {
    companion object {
        private const val TAG = "CloudSyncManager"
    }

    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    // Snapshot listeners for live multi-device sync
    private val activeListeners = mutableListOf<ListenerRegistration>()
    private var currentListeningUserId: String? = null

    init {
        // Observe auth changes to trigger sync and attach/detach real-time listeners
        scope.launch {
            authManager.currentUser.collect { user ->
                if (user != null && !user.isAnonymous) {
                    val uid = user.uid
                    if (currentListeningUserId != uid) {
                        Log.d(TAG, "User logged in: $uid (${user.provider}). Starting full sync & live listeners...")
                        stopRealtimeListeners()
                        triggerFullSync(uid)
                        startRealtimeListeners(uid)
                    }
                } else {
                    Log.d(TAG, "User logged out or guest. Stopping cloud listeners.")
                    stopRealtimeListeners()
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
                // 0. Sync and store User Profile in local Room DB & Firestore
                syncUserProfile(userId)

                // 1. Sync Favorites (Two-Way Merge)
                syncFavorites(userId)

                // 2. Sync Playlists & Playlist Tracks (Two-Way Merge)
                syncPlaylists(userId)

                // 3. Sync Recently Played History (Two-Way Merge)
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
    // 1. REAL-TIME SNAPSHOT LISTENERS (Multi-device live sync)
    // ──────────────────────────────────────────────────────────────

    private fun startRealtimeListeners(userId: String) {
        currentListeningUserId = userId
        try {
            // A. Favorites Listener
            val favListener = firestore.collection("users").document(userId).collection("favorites")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Favorites snapshot listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        scope.launch {
                            val remoteFavs = snapshots.documents.mapNotNull { doc ->
                                val trackId = doc.getString("trackId") ?: doc.id
                                val title = doc.getString("title") ?: return@mapNotNull null
                                FavoriteEntity(
                                    trackId = trackId,
                                    title = title,
                                    artistId = doc.getString("artistId") ?: "",
                                    artistName = doc.getString("artistName") ?: "",
                                    artistHandle = doc.getString("artistHandle"),
                                    artworkUrl = doc.getString("artworkUrl"),
                                    durationMs = doc.getLong("durationMs"),
                                    streamUrl = doc.getString("streamUrl"),
                                    genre = doc.getString("genre"),
                                    addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()
                                )
                            }
                            database.favoritesDao().insertFavorites(remoteFavs)
                        }
                    }
                }
            activeListeners.add(favListener)

            // B. Playlists Listener
            val playlistListener = firestore.collection("users").document(userId).collection("playlists")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "Playlists snapshot listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        scope.launch {
                            for (doc in snapshots.documents) {
                                val playlistIdLong = doc.id.toLongOrNull() ?: continue
                                val name = doc.getString("name") ?: continue
                                val entity = PlaylistEntity(
                                    id = playlistIdLong,
                                    name = name,
                                    description = doc.getString("description"),
                                    artworkUrl = doc.getString("artworkUrl"),
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                                )
                                database.playlistDao().insertPlaylist(entity)

                                // Fetch playlist tracks subcollection
                                fetchAndMergePlaylistTracks(userId, playlistIdLong)
                            }
                        }
                    }
                }
            activeListeners.add(playlistListener)

            // C. History Listener
            val historyListener = firestore.collection("users").document(userId).collection("history")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w(TAG, "History snapshot listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        scope.launch {
                            val items = snapshots.documents.mapNotNull { doc ->
                                val trackId = doc.getString("trackId") ?: doc.id
                                val title = doc.getString("title") ?: return@mapNotNull null
                                RecentlyPlayedEntity(
                                    trackId = trackId,
                                    title = title,
                                    artistId = doc.getString("artistId") ?: "",
                                    artistName = doc.getString("artistName") ?: "",
                                    artistHandle = doc.getString("artistHandle"),
                                    artworkUrl = doc.getString("artworkUrl"),
                                    durationMs = doc.getLong("durationMs"),
                                    streamUrl = doc.getString("streamUrl"),
                                    genre = doc.getString("genre"),
                                    playedAt = doc.getLong("playedAt") ?: System.currentTimeMillis()
                                )
                            }
                            database.recentlyPlayedDao().insertRecentlyPlayedList(items)
                        }
                    }
                }
            activeListeners.add(historyListener)

            Log.d(TAG, "Attached ${activeListeners.size} realtime Firestore listeners for user $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize realtime listeners: ${e.message}")
        }
    }

    private fun stopRealtimeListeners() {
        activeListeners.forEach { it.remove() }
        activeListeners.clear()
        currentListeningUserId = null
    }

    // ──────────────────────────────────────────────────────────────
    // 2. TWO-WAY MERGE ON INITIAL LOGIN
    // ──────────────────────────────────────────────────────────────

    private suspend fun syncUserProfile(userId: String) {
        val user = authManager.currentUser.value ?: return
        val userEntity = UserEntity(
            uid = user.uid,
            displayName = user.displayName,
            email = user.email,
            photoUrl = user.photoUrl,
            provider = user.provider.name,
            isAnonymous = user.isAnonymous,
            lastLoginAt = System.currentTimeMillis()
        )
        // Store in local SQLite Room DB
        database.userDao().insertUser(userEntity)

        // Store in Firestore cloud profile document
        val userDoc = firestore.collection("users").document(userId)
        val data = mapOf(
            "uid" to user.uid,
            "displayName" to (user.displayName ?: "Musync User"),
            "email" to (user.email ?: ""),
            "photoUrl" to (user.photoUrl ?: ""),
            "provider" to user.provider.name,
            "isAnonymous" to user.isAnonymous,
            "lastLoginAt" to System.currentTimeMillis(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userDoc.set(data, SetOptions.merge()).await()
    }

    private suspend fun syncFavorites(userId: String) {
        val favCollection = firestore.collection("users").document(userId).collection("favorites")

        // 1. Fetch remote favorites
        val remoteDocs = favCollection.get().await()
        val remoteMap = mutableMapOf<String, FavoriteEntity>()
        for (doc in remoteDocs.documents) {
            val trackId = doc.getString("trackId") ?: doc.id
            val title = doc.getString("title") ?: ""
            if (title.isBlank()) continue
            val artistId = doc.getString("artistId") ?: ""
            val artistName = doc.getString("artistName") ?: ""
            val artistHandle = doc.getString("artistHandle")
            val artworkUrl = doc.getString("artworkUrl")
            val durationMs = doc.getLong("durationMs")
            val streamUrl = doc.getString("streamUrl")
            val genre = doc.getString("genre")
            val addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()

            remoteMap[trackId] = FavoriteEntity(
                trackId = trackId,
                title = title,
                artistId = artistId,
                artistName = artistName,
                artistHandle = artistHandle,
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                streamUrl = streamUrl,
                genre = genre,
                addedAt = addedAt
            )
        }

        // 2. Fetch local favorites
        val localFavorites = database.favoritesDao().getAllFavorites().first()
        val localMap = localFavorites.associateBy { it.trackId }

        // 3. Merge remote into local Room DB
        val toInsertLocally = remoteMap.values.filter { !localMap.containsKey(it.trackId) }
        if (toInsertLocally.isNotEmpty()) {
            database.favoritesDao().insertFavorites(toInsertLocally)
        }

        // 4. Push local items missing from cloud in batches
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.trackId) }
        if (toPushRemotely.isNotEmpty()) {
            var batch = firestore.batch()
            var batchCount = 0
            for (localFav in toPushRemotely) {
                val docRef = favCollection.document(localFav.trackId)
                val data = mapOf(
                    "trackId" to localFav.trackId,
                    "title" to localFav.title,
                    "artistId" to localFav.artistId,
                    "artistName" to localFav.artistName,
                    "artistHandle" to localFav.artistHandle,
                    "artworkUrl" to localFav.artworkUrl,
                    "durationMs" to localFav.durationMs,
                    "streamUrl" to localFav.streamUrl,
                    "genre" to localFav.genre,
                    "addedAt" to localFav.addedAt
                )
                batch.set(docRef, data, SetOptions.merge())
                batchCount++
                if (batchCount >= 450) {
                    batch.commit().await()
                    batch = firestore.batch()
                    batchCount = 0
                }
            }
            if (batchCount > 0) {
                batch.commit().await()
            }
        }
    }

    private suspend fun syncPlaylists(userId: String) {
        val playlistCollection = firestore.collection("users").document(userId).collection("playlists")

        // 1. Fetch remote playlists
        val remoteDocs = playlistCollection.get().await()
        val remoteMap = mutableMapOf<Long, PlaylistEntity>()
        for (doc in remoteDocs.documents) {
            val idLong = doc.id.toLongOrNull() ?: continue
            val name = doc.getString("name") ?: "Playlist"
            val description = doc.getString("description")
            val artworkUrl = doc.getString("artworkUrl")
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

            remoteMap[idLong] = PlaylistEntity(
                id = idLong,
                name = name,
                description = description,
                artworkUrl = artworkUrl,
                createdAt = createdAt
            )
        }

        // 2. Fetch local playlists
        val localPlaylists = database.playlistDao().getAllPlaylists().first()
        val localMap = localPlaylists.associateBy { it.id }

        // 3. Merge remote playlists into local Room DB
        val toInsertLocally = remoteMap.values.filter { !localMap.containsKey(it.id) }
        if (toInsertLocally.isNotEmpty()) {
            database.playlistDao().insertPlaylists(toInsertLocally)
        }

        // 4. For every remote playlist, sync tracks subcollection
        for (playlistId in remoteMap.keys) {
            fetchAndMergePlaylistTracks(userId, playlistId)
        }

        // 5. Push local playlists missing from cloud
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.id) }
        if (toPushRemotely.isNotEmpty()) {
            for (pl in toPushRemotely) {
                val docRef = playlistCollection.document(pl.id.toString())
                val data = mapOf(
                    "name" to pl.name,
                    "description" to pl.description,
                    "artworkUrl" to pl.artworkUrl,
                    "createdAt" to pl.createdAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data, SetOptions.merge()).await()

                // Push its local tracks as well
                val tracks = database.playlistDao().getPlaylistTracksSync(pl.id)
                if (tracks.isNotEmpty()) {
                    var batch = firestore.batch()
                    var count = 0
                    for (track in tracks) {
                        val trackDoc = docRef.collection("tracks").document(track.trackId)
                        val trackData = mapOf(
                            "trackId" to track.trackId,
                            "title" to track.title,
                            "artistId" to track.artistId,
                            "artistName" to track.artistName,
                            "artistHandle" to track.artistHandle,
                            "artworkUrl" to track.artworkUrl,
                            "durationMs" to track.durationMs,
                            "streamUrl" to track.streamUrl,
                            "genre" to track.genre,
                            "position" to track.position,
                            "addedAt" to track.addedAt
                        )
                        batch.set(trackDoc, trackData, SetOptions.merge())
                        count++
                        if (count >= 450) {
                            batch.commit().await()
                            batch = firestore.batch()
                            count = 0
                        }
                    }
                    if (count > 0) {
                        batch.commit().await()
                    }
                }
            }
        }
    }

    private suspend fun fetchAndMergePlaylistTracks(userId: String, playlistId: Long) {
        try {
            val tracksCollection = firestore.collection("users").document(userId)
                .collection("playlists").document(playlistId.toString())
                .collection("tracks")

            val remoteTrackDocs = tracksCollection.get().await()
            val remoteItems = remoteTrackDocs.documents.mapNotNull { doc ->
                val trackId = doc.getString("trackId") ?: doc.id
                val title = doc.getString("title") ?: return@mapNotNull null
                PlaylistItemEntity(
                    playlistId = playlistId,
                    trackId = trackId,
                    title = title,
                    artistId = doc.getString("artistId") ?: "",
                    artistName = doc.getString("artistName") ?: "",
                    artistHandle = doc.getString("artistHandle"),
                    artworkUrl = doc.getString("artworkUrl"),
                    durationMs = doc.getLong("durationMs"),
                    streamUrl = doc.getString("streamUrl"),
                    genre = doc.getString("genre"),
                    position = (doc.getLong("position") ?: 0L).toInt(),
                    addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()
                )
            }
            if (remoteItems.isNotEmpty()) {
                database.playlistDao().insertPlaylistItems(remoteItems)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch tracks for playlist $playlistId: ${e.message}")
        }
    }

    private suspend fun syncRecentlyPlayed(userId: String) {
        val historyCollection = firestore.collection("users").document(userId).collection("history")

        // 1. Fetch remote history
        val remoteDocs = historyCollection.get().await()
        val remoteMap = mutableMapOf<String, RecentlyPlayedEntity>()
        for (doc in remoteDocs.documents) {
            val trackId = doc.getString("trackId") ?: doc.id
            val title = doc.getString("title") ?: ""
            if (title.isBlank()) continue
            val artistName = doc.getString("artistName") ?: ""
            val artworkUrl = doc.getString("artworkUrl")
            val durationMs = doc.getLong("durationMs")
            val streamUrl = doc.getString("streamUrl")
            val playedAt = doc.getLong("playedAt") ?: System.currentTimeMillis()

            remoteMap[trackId] = RecentlyPlayedEntity(
                trackId = trackId,
                title = title,
                artistId = doc.getString("artistId") ?: "",
                artistName = artistName,
                artistHandle = doc.getString("artistHandle"),
                artworkUrl = artworkUrl,
                durationMs = durationMs,
                streamUrl = streamUrl,
                genre = doc.getString("genre"),
                playedAt = playedAt
            )
        }

        // 2. Fetch local history
        val localHistory = database.recentlyPlayedDao().getRecentlyPlayed(50).first()
        val localMap = localHistory.associateBy { it.trackId }

        // 3. Merge remote into local Room DB
        val toInsertLocally = remoteMap.values.filter { !localMap.containsKey(it.trackId) }
        if (toInsertLocally.isNotEmpty()) {
            database.recentlyPlayedDao().insertRecentlyPlayedList(toInsertLocally)
        }

        // 4. Push local items missing from cloud
        val toPushRemotely = localMap.values.filter { !remoteMap.containsKey(it.trackId) }
        if (toPushRemotely.isNotEmpty()) {
            var batch = firestore.batch()
            var batchCount = 0
            for (item in toPushRemotely) {
                val docRef = historyCollection.document(item.trackId)
                val data = mapOf(
                    "trackId" to item.trackId,
                    "title" to item.title,
                    "artistId" to item.artistId,
                    "artistName" to item.artistName,
                    "artistHandle" to item.artistHandle,
                    "artworkUrl" to item.artworkUrl,
                    "durationMs" to item.durationMs,
                    "streamUrl" to item.streamUrl,
                    "genre" to item.genre,
                    "playedAt" to item.playedAt
                )
                batch.set(docRef, data, SetOptions.merge())
                batchCount++
                if (batchCount >= 450) {
                    batch.commit().await()
                    batch = firestore.batch()
                    batchCount = 0
                }
            }
            if (batchCount > 0) {
                batch.commit().await()
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 3. REAL-TIME ITEM MUTATION HOOKS (Invoked by Repositories)
    // ──────────────────────────────────────────────────────────────

    fun syncSingleFavorite(track: Track, isFavorite: Boolean) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        scope.launch {
            try {
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("favorites").document(track.id)
                if (isFavorite) {
                    val data = mapOf(
                        "trackId" to track.id,
                        "title" to track.title,
                        "artistId" to track.artist.id,
                        "artistName" to track.artist.name,
                        "artistHandle" to track.artist.handle,
                        "artworkUrl" to track.artworkUrl,
                        "durationMs" to track.durationMs,
                        "streamUrl" to track.streamUrl,
                        "genre" to track.genre,
                        "addedAt" to System.currentTimeMillis()
                    )
                    docRef.set(data, SetOptions.merge()).await()
                    Log.d(TAG, "Synced favorite added: ${track.id} (${track.title})")
                } else {
                    docRef.delete().await()
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
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("playlists").document(playlistId.toString())
                val data = mapOf(
                    "name" to name,
                    "description" to description,
                    "artworkUrl" to artworkUrl,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(data, SetOptions.merge()).await()
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
                val playlistDoc = firestore.collection("users").document(user.uid)
                    .collection("playlists").document(playlistId.toString())

                // Delete subcollection tracks
                val tracks = playlistDoc.collection("tracks").get().await()
                if (!tracks.isEmpty) {
                    val batch = firestore.batch()
                    tracks.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                }

                playlistDoc.delete().await()
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
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("playlists").document(playlistId.toString())
                    .collection("tracks").document(track.id)

                val data = mapOf(
                    "trackId" to track.id,
                    "title" to track.title,
                    "artistId" to track.artist.id,
                    "artistName" to track.artist.name,
                    "artistHandle" to track.artist.handle,
                    "artworkUrl" to track.artworkUrl,
                    "durationMs" to track.durationMs,
                    "streamUrl" to track.streamUrl,
                    "genre" to track.genre,
                    "position" to position,
                    "addedAt" to System.currentTimeMillis()
                )
                docRef.set(data, SetOptions.merge()).await()
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
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("playlists").document(playlistId.toString())
                    .collection("tracks").document(trackId)
                docRef.delete().await()
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
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("history").document(track.id)
                val data = mapOf(
                    "trackId" to track.id,
                    "title" to track.title,
                    "artistId" to track.artist.id,
                    "artistName" to track.artist.name,
                    "artistHandle" to track.artist.handle,
                    "artworkUrl" to track.artworkUrl,
                    "durationMs" to track.durationMs,
                    "streamUrl" to track.streamUrl,
                    "genre" to track.genre,
                    "playedAt" to System.currentTimeMillis()
                )
                docRef.set(data, SetOptions.merge()).await()
                Log.d(TAG, "Synced history track: ${track.id} (${track.title})")
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
                val historyDocs = firestore.collection("users").document(user.uid)
                    .collection("history").get().await()
                if (!historyDocs.isEmpty) {
                    val batch = firestore.batch()
                    historyDocs.documents.forEach { batch.delete(it.reference) }
                    batch.commit().await()
                    Log.d(TAG, "Synced history cleared in cloud")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing history in cloud: ${e.message}")
            }
        }
    }
}
