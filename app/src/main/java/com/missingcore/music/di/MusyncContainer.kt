package com.missingcore.music.di

import android.content.Context
import com.missingcore.music.data.api.UniversalMusicProvider
import com.missingcore.music.data.database.MusyncDatabase
import com.missingcore.music.data.datastore.PreferencesManager
import com.missingcore.music.data.repository.FavoritesRepositoryImpl
import com.missingcore.music.data.repository.MusicRepositoryImpl
import com.missingcore.music.data.repository.PlaylistRepositoryImpl
import com.missingcore.music.data.repository.RecentlyPlayedRepositoryImpl
import com.missingcore.music.domain.repository.FavoritesRepository
import com.missingcore.music.domain.repository.MusicRepository
import com.missingcore.music.domain.repository.PlaylistRepository
import com.missingcore.music.domain.repository.RecentlyPlayedRepository
import com.missingcore.music.playback.PlaybackManager
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

    val localAudioScanner: com.missingcore.music.data.local.LocalAudioScanner by lazy {
        com.missingcore.music.data.local.LocalAudioScanner(context)
    }

    val beatHapticManager: com.missingcore.music.playback.BeatHapticManager by lazy {
        com.missingcore.music.playback.BeatHapticManager(context)
    }

    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(context)
    }
}
