package com.musync.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musync.app.MainActivity
import com.musync.app.MusyncApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ROOT_ID = "musync_root"
        const val CATEGORY_TRENDING = "trending"
        const val CATEGORY_FAVORITES = "favorites"
        const val CATEGORY_RECENT = "recent"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(12000)
            .setDefaultRequestProperties(mapOf("Connection" to "keep-alive"))

        // Media3 Local Disk Cache (200MB LRU) for instant zero-bandwidth replays
        val cache = MediaCacheManager.getCache(this)
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, cacheDataSourceFactory)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        // High-fidelity & lossless buffer tuning: 500ms instant start, 30s-90s continuous buffer, 15s back-seek
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // minBufferMs
                90000, // maxBufferMs (lossless high-bitrate continuous audio)
                500,   // bufferForPlaybackMs (instant 500ms start)
                1000   // bufferForPlaybackAfterRebufferMs (ultra-fast recovery)
            )
            .setBackBuffer(15000, true) // 15s retention for instant backward seeking
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val app = application as MusyncApplication
        val beatHaptics = app.container.beatHapticManager
        val beatAudioProcessor = BeatDetectorAudioProcessor(beatHaptics)

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true) // Lossless 32-bit float audio pipeline
                    .setAudioProcessors(arrayOf(beatAudioProcessor))
                    .build()
            }
        }

        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        val explicitSessionId = try {
            audioManager.generateAudioSessionId()
        } catch (_: Exception) {
            0
        }

        if (explicitSessionId != 0) {
            android.util.Log.d("MusicPlaybackService", "Generated explicit audioSessionId: $explicitSessionId")
            app.container.audioEffectManager.attach(explicitSessionId)
        }

        exoPlayer.addAnalyticsListener(object : androidx.media3.exoplayer.analytics.AnalyticsListener {
            override fun onAudioSessionIdChanged(
                eventTime: androidx.media3.exoplayer.analytics.AnalyticsListener.EventTime,
                audioSessionId: Int
            ) {
                android.util.Log.d("MusicPlaybackService", "AnalyticsListener -> onAudioSessionIdChanged: $audioSessionId")
                if (audioSessionId != 0) {
                    app.container.audioEffectManager.attach(audioSessionId)
                }
            }
        })

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        exoPlayer.addListener(object : Player.Listener {
            private var playbackStartTime = 0L

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    playbackStartTime = System.currentTimeMillis()
                } else {
                    // If played for more than 5 seconds, record in recently played
                    val elapsed = System.currentTimeMillis() - playbackStartTime
                    if (elapsed >= 5000L) {
                        recordCurrentTrack()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                playbackStartTime = System.currentTimeMillis()
                if (mediaItem != null) {
                    recordCurrentTrack()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN ($playbackState)"
                }
                val currentItem = exoPlayer.currentMediaItem
                android.util.Log.d("MusicPlaybackService", "ExoPlayer State Changed: $stateName | Current Media: ${currentItem?.mediaId} | URI: ${currentItem?.requestMetadata?.mediaUri ?: currentItem?.localConfiguration?.uri}")
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentItem = exoPlayer.currentMediaItem
                val uri = currentItem?.requestMetadata?.mediaUri ?: currentItem?.localConfiguration?.uri
                android.util.Log.e(
                    "MusicPlaybackService",
                    "ExoPlayer Error occurred | MediaId: ${currentItem?.mediaId} | Title: ${currentItem?.mediaMetadata?.title} | URI: $uri | ErrorCode: ${error.errorCode} | ErrorCodeName: ${error.errorCodeName} | Message: ${error.message}",
                    error
                )
            }
        })

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, CustomLibraryCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun recordCurrentTrack() {
        val currentMediaItem = player?.currentMediaItem ?: return
        val track = MediaItemMapper.fromMediaItem(currentMediaItem)
        serviceScope.launch(Dispatchers.IO) {
            try {
                val app = application as MusyncApplication
                app.container.recentlyPlayedRepository.recordPlayed(track)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        val app = application as MusyncApplication
        app.container.audioEffectManager.detach()
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    private inner class CustomLibraryCallback : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val app = application as MusyncApplication
            val future = com.google.common.util.concurrent.SettableFuture.create<MutableList<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                val resolvedItems = mediaItems.map { item ->
                    val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                    val uriStr = uri?.toString()
                    val track = MediaItemMapper.fromMediaItem(item)

                    val resolvedStreamUrl = if (!uriStr.isNullOrBlank() && uri != android.net.Uri.EMPTY) {
                        uriStr
                    } else {
                        try {
                            app.container.universalMusicProvider.getStreamUrl(track)
                        } catch (e: Exception) {
                            android.util.Log.w("MusicPlaybackService", "Failed resolving stream for ${track.id}: ${e.message}")
                            null
                        }
                    }

                    if (!resolvedStreamUrl.isNullOrBlank()) {
                        android.util.Log.d("MusicPlaybackService", "onAddMediaItems -> resolved stream for '${track.title}' (${track.id}) -> $resolvedStreamUrl")
                        MediaItemMapper.toMediaItem(track.copy(streamUrl = resolvedStreamUrl))
                    } else {
                        android.util.Log.w("MusicPlaybackService", "onAddMediaItems -> no stream URL for '${track.title}' (${track.id})")
                        item.buildUpon().setUri(uri ?: android.net.Uri.EMPTY).build()
                    }
                }.toMutableList()
                future.set(resolvedItems)
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Musync Catalog")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when (parentId) {
                ROOT_ID -> {
                    val items = listOf(
                        createBrowsableCategory(CATEGORY_TRENDING, "Trending Music"),
                        createBrowsableCategory(CATEGORY_FAVORITES, "Favorites"),
                        createBrowsableCategory(CATEGORY_RECENT, "Recently Played")
                    )
                    Futures.immediateFuture(LibraryResult.ofItemList(items, params))
                }
                CATEGORY_FAVORITES -> {
                    val app = application as MusyncApplication
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val favorites = app.container.favoritesRepository.getFavorites().first()
                            val mediaItems = favorites.map { MediaItemMapper.toMediaItem(it) }
                            future.set(LibraryResult.ofItemList(mediaItems, params))
                        } catch (e: Exception) {
                            future.set(LibraryResult.ofItemList(emptyList(), params))
                        }
                    }
                    future
                }
                CATEGORY_RECENT -> {
                    val app = application as MusyncApplication
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val recents = app.container.recentlyPlayedRepository.getRecentlyPlayed(20).first()
                            val mediaItems = recents.map { MediaItemMapper.toMediaItem(it) }
                            future.set(LibraryResult.ofItemList(mediaItems, params))
                        } catch (e: Exception) {
                            future.set(LibraryResult.ofItemList(emptyList(), params))
                        }
                    }
                    future
                }
                CATEGORY_TRENDING -> {
                    val app = application as MusyncApplication
                    val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val result = app.container.musicRepository.getTrending()
                            val tracks = result.getOrDefault(emptyList())
                            val mediaItems = tracks.map { MediaItemMapper.toMediaItem(it) }
                            future.set(LibraryResult.ofItemList(mediaItems, params))
                        } catch (e: Exception) {
                            future.set(LibraryResult.ofItemList(emptyList(), params))
                        }
                    }
                    future
                }
                else -> Futures.immediateFuture(LibraryResult.ofItemList(emptyList(), params))
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val app = application as MusyncApplication
            val future = com.google.common.util.concurrent.SettableFuture.create<LibraryResult<MediaItem>>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val trackResult = app.container.musicRepository.getTrack(mediaId)
                    val track = trackResult.getOrNull()
                    if (track != null) {
                        future.set(LibraryResult.ofItem(MediaItemMapper.toMediaItem(track), null))
                    } else {
                        future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE))
                    }
                } catch (e: Exception) {
                    future.set(LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN))
                }
            }
            return future
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val app = application as MusyncApplication
            val future = com.google.common.util.concurrent.SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch(Dispatchers.IO) {
                try {
                    val recent = app.container.recentlyPlayedRepository.getRecentlyPlayed(10).first()
                    val mediaItems = recent.map { MediaItemMapper.toMediaItem(it) }
                    future.set(MediaSession.MediaItemsWithStartPosition(mediaItems, 0, 0L))
                } catch (_: Exception) {
                    future.set(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
                }
            }
            return future
        }

        private fun createBrowsableCategory(id: String, title: String): MediaItem {
            return MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .build()
                )
                .build()
        }
    }
}

