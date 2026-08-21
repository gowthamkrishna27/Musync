package com.musync.app.core.di

import android.content.Context
import com.musync.app.data.remote.UniversalMusicProvider
import com.musync.app.data.local.database.MusyncDatabase
import com.musync.app.data.local.datastore.PreferencesManager
import com.musync.app.data.repository.FavoritesRepositoryImpl
import com.musync.app.data.repository.MusicRepositoryImpl
import com.musync.app.data.repository.PlaylistRepositoryImpl
import com.musync.app.data.repository.RecentlyPlayedRepositoryImpl
import com.musync.app.domain.repository.FavoritesRepository
import com.musync.app.domain.repository.MusicRepository
import com.musync.app.domain.repository.PlaylistRepository
import com.musync.app.domain.repository.RecentlyPlayedRepository
import com.musync.app.playback.PlaybackManager
import com.musync.app.playback.recommendation.RealTimeRecommendationEngine
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
                // Keep session tracker in sync with the active backend URL
                playbackManager.sessionTracker.backendBaseUrl = activeUrl
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
            favoritesDao = database.favoritesDao(),
            cloudSyncManager = cloudSyncManager
        )
    }

    val playlistRepository: PlaylistRepository by lazy {
        PlaylistRepositoryImpl(
            playlistDao = database.playlistDao(),
            cloudSyncManager = cloudSyncManager
        )
    }

    val recentlyPlayedRepository: RecentlyPlayedRepository by lazy {
        RecentlyPlayedRepositoryImpl(
            recentlyPlayedDao = database.recentlyPlayedDao(),
            cloudSyncManager = cloudSyncManager
        )
    }

    val localAudioScanner: com.musync.app.data.local.scanner.LocalAudioScanner by lazy {
        com.musync.app.data.local.scanner.LocalAudioScanner(context)
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

    val musyncDownloadManager: com.musync.app.data.download.MusyncDownloadManager by lazy {
        com.musync.app.data.download.MusyncDownloadManager(
            context = context,
            downloadDao = database.downloadDao(),
            preferencesManager = preferencesManager,
            universalMusicProvider = universalMusicProvider,
            playbackStateProvider = { playbackManager.playbackState.value }
        )
    }

    val downloadRepository: com.musync.app.domain.repository.DownloadRepository by lazy {
        com.musync.app.data.repository.DownloadRepositoryImpl(
            downloadDao = database.downloadDao(),
            downloadManager = musyncDownloadManager
        )
    }

    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(context)
    }

    val realTimeRecommendationEngine: RealTimeRecommendationEngine by lazy {
        RealTimeRecommendationEngine(
            scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            ),
            musicRepository = musicRepository,
            favoritesRepository = favoritesRepository,
            recentlyPlayedRepository = recentlyPlayedRepository,
            sessionTracker = playbackManager.sessionTracker,
            preferencesManager = preferencesManager
        )
    }
}

