package com.musync.app.di

import android.content.Context
import com.musync.app.data.api.UniversalMusicProvider
import com.musync.app.data.database.MusyncDatabase
import com.musync.app.data.datastore.PreferencesManager
import com.musync.app.data.repository.FavoritesRepositoryImpl
import com.musync.app.data.repository.MusicRepositoryImpl
import com.musync.app.data.repository.PlaylistRepositoryImpl
import com.musync.app.data.repository.RecentlyPlayedRepositoryImpl
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
import com.musync.app.playback.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusyncContainer(private val context: Context) {

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    val database: MusyncDatabase by lazy {
        MusyncDatabase.getDatabase(context)
    }

    val universalMusicProvider: UniversalMusicProvider by lazy {
        UniversalMusicProvider().apply {
            updateConfiguration(preferencesManager.getBaseUrl(), preferencesManager.getApiKey())
        }
    }

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            preferencesManager.baseUrl.collect { url ->
                val activeUrl = if (url == "none") "" else url
                universalMusicProvider.updateConfiguration(activeUrl, preferencesManager.getApiKey())
            }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            preferencesManager.audioQuality.collect { quality ->
                universalMusicProvider.updateAudioQuality(quality)
            }
        }
    }

    val musicRepository: MusicRepository by lazy {
        MusicRepositoryImpl(
            provider = universalMusicProvider,
            trackCacheDao = database.trackCacheDao()
        )
    }

    val favoritesRepository: FavoritesRepository by lazy {
        FavoritesRepositoryImpl(
            favoritesDao = database.favoritesDao()
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(
            playlistDao = database.playlistDao()
        )
    }

    val recentlyPlayedRepository: RecentlyPlayedRepository by lazy {
        RecentlyPlayedRepositoryImpl(
            recentlyPlayedDao = database.recentlyPlayedDao()
        )
    }

    val localAudioScanner: com.musync.app.data.local.LocalAudioScanner by lazy {
        com.musync.app.data.local.LocalAudioScanner(context)
    }

    val beatHapticManager: com.musync.app.playback.BeatHapticManager by lazy {
        com.musync.app.playback.BeatHapticManager(context)
    }

    val audioEffectManager: com.musync.app.playback.AudioEffectManager by lazy {
        com.musync.app.playback.AudioEffectManager(context, preferencesManager)
    }

    val appUpdateManager: com.musync.app.update.AppUpdateManager by lazy {
        com.musync.app.update.AppUpdateManager(context, preferencesManager)
    }

    val authManager: com.musync.app.auth.AuthManager by lazy {
        com.musync.app.auth.AuthManager(context)
    }

    val cloudSyncManager: com.musync.app.data.sync.CloudSyncManager by lazy {
        com.musync.app.data.sync.CloudSyncManager(authManager, database)
    }

    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(context)
    }
}

