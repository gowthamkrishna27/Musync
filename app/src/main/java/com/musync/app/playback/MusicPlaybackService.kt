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
import com.musync.app.domain.model.Track
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

    private lateinit var trackPreloadManager: TrackPreloadManager
    private var previousTrackEndTime = 0L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        trackPreloadManager = TrackPreloadManager(this, serviceScope)

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
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

        // Resilient Low-Latency & Anti-Stutter Buffer Tuning:
        // - 1.0s initial buffer for instantaneous startup
        // - 2.0s rebuffer recovery threshold
        // - 30s-60s continuous safety buffer
        // - 15s back-buffer for instant back-seeking
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000, // minBufferMs (30s)
                60000, // maxBufferMs (60s)
                1000,  // bufferForPlaybackMs (instant 1.0s startup)
                2000   // bufferForPlaybackAfterRebufferMs (fast 2.0s rebuffer recovery)
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
                    .setEnableFloatOutput(false) // Native 16-bit PCM pipeline (low CPU & hardware compatibility)
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
            private var rebufferCount = 0
            private var rebufferStartTime = 0L
            private var totalRebufferDurationMs = 0L
            private var isInitialBuffering = true
            private var trackFailureCount = 0

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    playbackStartTime = System.currentTimeMillis()
                    triggerNextTrackPreload()
                } else {
                    val elapsed = System.currentTimeMillis() - playbackStartTime
                    if (elapsed >= 5000L) {
                        recordCurrentTrack()
                    }
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                triggerNextTrackPreload()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val now = System.currentTimeMillis()
                if (previousTrackEndTime > 0L) {
                    val gapMs = (now - previousTrackEndTime).coerceAtLeast(0L)
                    android.util.Log.i("MusicPlaybackService", "⚡ GAPLESS TRACK TRANSITION: gap = ${gapMs}ms (Reason: $reason, New Track: ${mediaItem?.mediaMetadata?.title})")
                }
                previousTrackEndTime = 0L
                playbackStartTime = now
                rebufferCount = 0
                totalRebufferDurationMs = 0L
                isInitialBuffering = true
                trackFailureCount = 0

                if (mediaItem != null) {
                    recordCurrentTrack()
                    triggerNextTrackPreload()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val currentPos = exoPlayer.currentPosition.coerceAtLeast(0L)
                val bufferedPos = exoPlayer.bufferedPosition.coerceAtLeast(0L)
                val safetyDurationMs = (bufferedPos - currentPos).coerceAtLeast(0L)
                val safetySec = safetyDurationMs / 1000.0

                val posFormatted = String.format("%02d:%02d", (currentPos / 1000) / 60, (currentPos / 1000) % 60)
                val bufFormatted = String.format("%02d:%02d", (bufferedPos / 1000) / 60, (bufferedPos / 1000) % 60)

                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> {
                        if (!isInitialBuffering) {
                            rebufferCount++
                            rebufferStartTime = System.currentTimeMillis()
                            android.util.Log.w("MusicPlaybackService", "⚠️ REBUFFER EVENT #$rebufferCount | Safety Cushion: ${safetySec}s | Current = $posFormatted | Buffered = $bufFormatted")
                        } else {
                            android.util.Log.d("MusicPlaybackService", "Initial Buffering... | Current = $posFormatted | Buffered = $bufFormatted")
                        }
                        "BUFFERING"
                    }
                    Player.STATE_READY -> {
                        if (rebufferStartTime > 0L) {
                            val duration = System.currentTimeMillis() - rebufferStartTime
                            totalRebufferDurationMs += duration
                            rebufferStartTime = 0L
                            android.util.Log.d("MusicPlaybackService", "✓ REBUFFER RECOVERED in ${duration}ms (Total rebuffer time: ${totalRebufferDurationMs}ms, Events: $rebufferCount)")
                        }
                        isInitialBuffering = false
                        "READY"
                    }
                    Player.STATE_ENDED -> {
                        previousTrackEndTime = System.currentTimeMillis()
                        "ENDED"
                    }
                    else -> "UNKNOWN ($playbackState)"
                }

                val currentItem = exoPlayer.currentMediaItem
                android.util.Log.d(
                    "MusicPlaybackService",
                    "ExoPlayer State: $stateName | Buffer Depth: [Current: $posFormatted, Buffered: $bufFormatted, Safety: ${String.format("%.1f", safetySec)}s] | Rebuffers: $rebufferCount | Track: ${currentItem?.mediaId}"
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val currentItem = exoPlayer.currentMediaItem
                val uri = currentItem?.requestMetadata?.mediaUri ?: currentItem?.localConfiguration?.uri
                android.util.Log.e(
                    "MusicPlaybackService",
                    "ExoPlayer Error occurred | MediaId: ${currentItem?.mediaId} | Title: ${currentItem?.mediaMetadata?.title} | URI: $uri | ErrorCode: ${error.errorCode} | ErrorCodeName: ${error.errorCodeName} | Message: ${error.message}",
                    error
                )

                // Intelligent Error Recovery: Retry current track once; if still failing, skip to preloaded next track
                trackFailureCount++
                if (trackFailureCount <= 1) {
                    android.util.Log.w("MusicPlaybackService", "Retrying failed track (attempt #$trackFailureCount)...")
                    exoPlayer.prepare()
                    exoPlayer.play()
                } else {
                    android.util.Log.w("MusicPlaybackService", "Skipping unplayable track to keep continuous playback alive...")
                    if (exoPlayer.hasNextMediaItem()) {
                        exoPlayer.seekToNextMediaItem()
                        exoPlayer.prepare()
                        exoPlayer.play()
                    }
                }
            }
        })

        this.player = exoPlayer

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, CustomLibraryCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    private fun triggerNextTrackPreload() {
        val p = this.player ?: return
        val currentIndex = p.currentMediaItemIndex
        val count = p.mediaItemCount
        if (count <= 0 || currentIndex !in 0 until count) return

        val queue = mutableListOf<Track>()
        for (i in 0 until count) {
            val item = p.getMediaItemAt(i)
            queue.add(MediaItemMapper.fromMediaItem(item))
        }

        val app = application as MusyncApplication
        val baseUrl = app.container.preferencesManager.getBaseUrl()
        trackPreloadManager.onTrackPlaying(currentIndex, queue, baseUrl)
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
        trackPreloadManager.clear()
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
            val future = com.google.common.util.concurrent.SettableFuture.create<MutableList<MediaItem>>()
            val resolvedItems = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                val uriStr = uri?.toString()

                if (!uriStr.isNullOrBlank() && uri != android.net.Uri.EMPTY) {
                    item.buildUpon().setUri(uri).build()
                } else {
                    val track = MediaItemMapper.fromMediaItem(item)
                    MediaItemMapper.toMediaItem(track)
                }
            }.toMutableList()
            future.set(resolvedItems)
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

