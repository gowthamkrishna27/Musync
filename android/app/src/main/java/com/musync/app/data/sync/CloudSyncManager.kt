package com.musync.app.data.sync

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.musync.app.auth.AuthManager
import com.musync.app.auth.CloudSyncStatus
import com.musync.app.data.local.database.MusyncDatabase
import com.musync.app.data.local.database.entity.FavoriteEntity
import com.musync.app.data.local.database.entity.PlaylistEntity
import com.musync.app.data.local.database.entity.RecentlyPlayedEntity
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

    init {
        // Observe auth changes to trigger sync on login
        scope.launch {
            authManager.currentUser.collect { user ->
                if (user != null && !user.isAnonymous) {
                    Log.d(TAG, "User logged in: ${user.uid} (${user.provider}). Starting full sync...")
                    triggerFullSync(user.uid)
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

                // 2. Sync Playlists
                syncPlaylists(userId)

                // 3. Sync Recently Played History
                syncRecentlyPlayed(userId)

                authManager.setSyncStatus(CloudSyncStatus.SYNCED)
                Log.d(TAG, "Cloud sync complete for user $userId")
            } catch (e: Exception) {
                Log.e(TAG, "Cloud sync failed", e)
                authManager.setSyncStatus(CloudSyncStatus.ERROR)
            }
        }
    }

    private suspend fun syncUserProfile(userId: String) {
        val user = authManager.currentUser.value ?: return
        val userEntity = com.musync.app.data.local.database.entity.UserEntity(
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
            "lastLoginAt" to System.currentTimeMillis()
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

        // Merge remote items into local Room DB
        for ((trackId, remoteFav) in remoteMap) {
            if (!localMap.containsKey(trackId)) {
                database.favoritesDao().insertFavorite(remoteFav)
            }
        }

        // Push local items missing from cloud in a single batch
        val batch = firestore.batch()
        var batchCount = 0
        for ((trackId, localFav) in localMap) {
            if (!remoteMap.containsKey(trackId)) {
                val docRef = favCollection.document(trackId)
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
                    batchCount = 0
                }
            }
        }
        if (batchCount > 0) {
            batch.commit().await()
        }
    }

    private suspend fun syncPlaylists(userId: String) {
        val playlistCollection = firestore.collection("users").document(userId).collection("playlists")
        val localPlaylists = database.playlistDao().getAllPlaylists().first()

        val batch = firestore.batch()
        var count = 0
        for (pl in localPlaylists) {
            val docRef = playlistCollection.document(pl.id.toString())
            val data = mapOf(
                "name" to pl.name,
                "description" to pl.description,
                "artworkUrl" to pl.artworkUrl,
                "createdAt" to pl.createdAt
            )
            batch.set(docRef, data, SetOptions.merge())
            count++
            if (count >= 450) {
                batch.commit().await()
                count = 0
            }
        }
        if (count > 0) {
            batch.commit().await()
        }
    }

    private suspend fun syncRecentlyPlayed(userId: String) {
        val historyCollection = firestore.collection("users").document(userId).collection("history")
        val localHistory = database.recentlyPlayedDao().getRecentlyPlayed(20).first()

        val batch = firestore.batch()
        var count = 0
        for (item in localHistory) {
            val docRef = historyCollection.document(item.trackId)
            val data = mapOf(
                "trackId" to item.trackId,
                "title" to item.title,
                "artistName" to item.artistName,
                "artworkUrl" to item.artworkUrl,
                "durationMs" to item.durationMs,
                "streamUrl" to item.streamUrl,
                "playedAt" to item.playedAt
            )
            batch.set(docRef, data, SetOptions.merge())
            count++
            if (count >= 450) {
                batch.commit().await()
                count = 0
            }
        }
        if (count > 0) {
            batch.commit().await()
        }
    }

    suspend fun syncSingleFavorite(track: Track, isFavorite: Boolean) {
        val user = authManager.currentUser.value ?: return
        if (user.isAnonymous) return

        try {
            val docRef = firestore.collection("users").document(user.uid).collection("favorites").document(track.id)
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
            } else {
                docRef.delete().await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sync single favorite", e)
        }
    }
}
