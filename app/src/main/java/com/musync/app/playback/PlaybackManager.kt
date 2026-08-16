package com.musync.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musync.app.domain.model.PlaybackState
import com.musync.app.domain.model.RepeatMode
import com.musync.app.domain.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PlaybackManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val currentQueue = mutableListOf<Track>()
    private var retryCount = 0
    private val maxRetries = 3

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val controller = controllerFuture?.get()
                    mediaController = controller
                    if (controller != null) {
                        setupPlayerListener(controller)
                        updateStateFromController()
                    }
                } catch (e: Exception) {
                    _playbackState.update { it.copy(errorMessage = "Failed to connect to player service: ${e.message}") }
                }
            },
            androidx.core.content.ContextCompat.getMainExecutor(context)
        )
    }

    private fun setupPlayerListener(player: Player?) {
        player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(playbackStateInt: Int) {
                if (playbackStateInt == Player.STATE_READY) {
                    retryCount = 0
                }
                val isBuffering = playbackStateInt == Player.STATE_BUFFERING
                _playbackState.update { it.copy(isBuffering = isBuffering) }
                updateStateFromController()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                retryCount = 0
                updateCurrentTrackFromController()
            }

            override fun onRepeatModeChanged(repeatModeInt: Int) {
                val mode = when (repeatModeInt) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                _playbackState.update { it.copy(repeatMode = mode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(isShuffle = shuffleModeEnabled) }
            }

            override fun onPlayerError(error: PlaybackException) {
                val item = mediaController?.currentMediaItem
                val uri = item?.requestMetadata?.mediaUri ?: item?.localConfiguration?.uri
                android.util.Log.e(
                    "PlaybackManager",
                    "ExoPlayer playback error (attempt $retryCount/$maxRetries) | Track: ${item?.mediaMetadata?.title} (${item?.mediaId}) | URI: $uri | ErrorCode: ${error.errorCode} (${error.errorCodeName}) | Message: ${error.message}",
                    error
                )

                // Resilient exponential backoff retry with jitter for temporary network interruptions
                if (retryCount < maxRetries) {
                    retryCount++
                    val backoffDelay = (600L * (1 shl (retryCount - 1))) + kotlin.random.Random.nextLong(100, 300)
                    android.util.Log.w("PlaybackManager", "Network hiccup encountered. Retrying playback (attempt $retryCount) in ${backoffDelay}ms...")
                    scope.launch {
                        delay(backoffDelay)
                        mediaController?.let { controller ->
                            controller.prepare()
                            controller.play()
                        }
                    }
                    return
                }

                // If retries exhausted and next track exists, skip gracefully
                retryCount = 0
                if (currentQueue.size > 1 && mediaController?.hasNextMediaItem() == true) {
                    android.util.Log.w("PlaybackManager", "Track unrecoverable, skipping to next track in queue...")
                    mediaController?.seekToNextMediaItem()
                    return
                }

                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "Streaming error (${error.errorCodeName}): ${error.message ?: "Failed to load audio stream"}"
                    )
                }
            }
        })
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem
        val currentTrack = currentItem?.let { MediaItemMapper.fromMediaItem(it) }
        val duration = if (controller.duration > 0) controller.duration else (currentTrack?.durationMs ?: 0L)
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val buf = controller.bufferedPosition.coerceAtLeast(0L)

        _playbackState.update {
            it.copy(
                currentTrack = currentTrack,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = pos,
                bufferedPositionMs = buf,
                durationMs = duration,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                },
                isShuffle = controller.shuffleModeEnabled,
                queue = currentQueue.toList(),
                queueIndex = controller.currentMediaItemIndex
            )
        }
    }

    private fun updateCurrentTrackFromController() {
        val controller = mediaController ?: return
        val item = controller.currentMediaItem
        val track = item?.let { MediaItemMapper.fromMediaItem(it) }
        val index = controller.currentMediaItemIndex
        val buf = controller.bufferedPosition.coerceAtLeast(0L)

        _playbackState.update {
            it.copy(
                currentTrack = track,
                queueIndex = index,
                currentPositionMs = 0L,
                bufferedPositionMs = buf,
                durationMs = if (controller.duration > 0) controller.duration else (track?.durationMs ?: 0L)
            )
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        val pos = controller.currentPosition.coerceAtLeast(0L)
                        val buf = controller.bufferedPosition.coerceAtLeast(0L)
                        val dur = if (controller.duration > 0) controller.duration else (_playbackState.value.currentTrack?.durationMs ?: 0L)
                        _playbackState.update {
                            it.copy(
                                currentPositionMs = pos,
                                bufferedPositionMs = buf,
                                durationMs = dur
                            )
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        val controller = mediaController
        if (controller != null) {
            action(controller)
        } else {
            controllerFuture?.addListener(
                {
                    try {
                        val newController = controllerFuture?.get()
                        if (newController != null) {
                            mediaController = newController
                            action(newController)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlaybackManager", "withController failed: ${e.message}", e)
                    }
                },
                androidx.core.content.ContextCompat.getMainExecutor(context)
            )
        }
    }

    private var playbackRequestId = 0L

    fun play(track: Track) {
        val requestId = ++playbackRequestId
        currentQueue.clear()
        currentQueue.add(track)

        val mediaItem = MediaItemMapper.toMediaItem(track)
        withController { controller ->
            if (requestId != playbackRequestId) return@withController
            android.util.Log.i("PlaybackManager", "▶ USER_SELECTED single track (Req #$requestId): '${track.title}' (${track.id}) | URI: ${mediaItem.requestMetadata.mediaUri}")
            controller.setMediaItems(listOf(mediaItem), 0, 0L)
            controller.prepare()
            controller.play()
        }

        _playbackState.update {
            it.copy(
                queue = currentQueue.toList(),
                queueIndex = 0,
                currentTrack = track,
                errorMessage = null
            )
        }
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val requestId = ++playbackRequestId
        val incomingTracks = tracks.toList()
        currentQueue.clear()
        currentQueue.addAll(incomingTracks)

        val mediaItems = incomingTracks.map { MediaItemMapper.toMediaItem(it) }
        val safeIndex = if (mediaItems.isNotEmpty()) startIndex.coerceIn(0, mediaItems.size - 1) else 0

        withController { controller ->
            if (requestId != playbackRequestId) {
                android.util.Log.d("PlaybackManager", "playTracks ignored stale request #$requestId (current: #$playbackRequestId)")
                return@withController
            }
            if (mediaItems.isNotEmpty()) {
                val currentTrackUri = mediaItems[safeIndex].requestMetadata.mediaUri
                android.util.Log.i("PlaybackManager", "▶ USER_SELECTED (Req #$requestId): '${incomingTracks[safeIndex].title}' (${incomingTracks[safeIndex].id}) at index $safeIndex/${mediaItems.size} | URI: $currentTrackUri")
                controller.setMediaItems(mediaItems, safeIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }

        _playbackState.update {
            it.copy(
                queue = currentQueue.toList(),
                queueIndex = safeIndex,
                currentTrack = incomingTracks.getOrNull(safeIndex),
                errorMessage = null
            )
        }
    }

    fun playAtIndex(index: Int) {
        if (index in currentQueue.indices) {
            val requestId = ++playbackRequestId
            withController { controller ->
                if (requestId != playbackRequestId) return@withController
                controller.seekTo(index, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        withController { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun pause() {
        withController { it.pause() }
    }

    fun resume() {
        withController { it.play() }
    }

    fun stop() {
        withController { it.stop() }
        stopProgressTracker()
    }

    fun seekTo(positionMs: Long) {
        withController { it.seekTo(positionMs) }
        _playbackState.update { it.copy(currentPositionMs = positionMs) }
    }

    fun skipNext() {
        withController { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
                controller.play()
            }
        }
    }

    fun skipPrevious() {
        withController { controller ->
            if (controller.currentPosition > 3000) {
                controller.seekTo(0L)
            } else if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
                controller.play()
            } else {
                controller.seekTo(0L)
            }
        }
    }

    fun addToQueue(track: Track) {
        currentQueue.add(track)
        withController { it.addMediaItem(MediaItemMapper.toMediaItem(track)) }
        _playbackState.update { it.copy(queue = currentQueue.toList()) }
    }

    fun playNext(track: Track) {
        withController { controller ->
            val nextIndex = (controller.currentMediaItemIndex + 1).coerceAtMost(currentQueue.size)
            currentQueue.add(nextIndex, track)
            controller.addMediaItem(nextIndex, MediaItemMapper.toMediaItem(track))
            _playbackState.update { it.copy(queue = currentQueue.toList()) }
        }
    }

    fun removeFromQueue(trackId: String) {
        val index = currentQueue.indexOfFirst { it.id == trackId }
        if (index >= 0) {
            currentQueue.removeAt(index)
            withController { it.removeMediaItem(index) }
            _playbackState.update { it.copy(queue = currentQueue.toList()) }
        }
    }

    fun clearQueue() {
        val current = _playbackState.value.currentTrack
        currentQueue.clear()
        withController { controller ->
            if (current != null) {
                currentQueue.add(current)
                controller.setMediaItems(listOf(MediaItemMapper.toMediaItem(current)))
            } else {
                controller.clearMediaItems()
            }
        }
        _playbackState.update { it.copy(queue = currentQueue.toList()) }
    }

    fun setShuffle(enabled: Boolean) {
        withController { it.shuffleModeEnabled = enabled }
        _playbackState.update { it.copy(isShuffle = enabled) }
    }

    fun setRepeatMode(mode: RepeatMode) {
        val exoRepeat = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        withController { it.repeatMode = exoRepeat }
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    fun release() {
        stopProgressTracker()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}

