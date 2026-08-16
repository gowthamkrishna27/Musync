package com.musync.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.MediaType
import com.musync.app.domain.model.Track

object MediaItemMapper {

    private const val EXTRA_TRACK_ID = "musync_extra_track_id"
    private const val EXTRA_ARTIST_ID = "musync_extra_artist_id"
    private const val EXTRA_GENRE = "musync_extra_genre"
    private const val EXTRA_MEDIA_TYPE = "musync_extra_media_type"
    private const val EXTRA_VIDEO_URL = "musync_extra_video_url"
    private const val EXTRA_VIDEO_AVAILABLE = "musync_extra_video_available"
    private const val EXTRA_VIDEO_QUALITY = "musync_extra_video_quality"
    private const val DEFAULT_BASE_URL = "https://musync-production-2fc5.up.railway.app"

    fun toMediaItem(
        track: Track,
        forceVideo: Boolean = false,
        videoQuality: String = "auto",
        baseUrl: String = DEFAULT_BASE_URL
    ): MediaItem {
        val isVideo = forceVideo || track.mediaType == MediaType.VIDEO
        val cleanId = track.id.removePrefix("yt_")

        val targetStreamUrl = if (isVideo) {
            if (!track.videoUrl.isNullOrBlank()) {
                track.videoUrl
            } else if (track.id.startsWith("content://") || track.id.startsWith("file://")) {
                track.id
            } else {
                "$baseUrl/stream?id=$cleanId&type=video&quality=$videoQuality"
            }
        } else {
            if (!track.streamUrl.isNullOrBlank() && !track.streamUrl.contains("type=video")) {
                track.streamUrl
            } else if (track.id.startsWith("content://") || track.id.startsWith("file://")) {
                track.id
            } else {
                "$baseUrl/stream?id=$cleanId&quality=low"
            }
        }

        val videoEndpointUrl = if (!track.videoUrl.isNullOrBlank()) {
            track.videoUrl
        } else {
            "$baseUrl/stream?id=$cleanId&type=video&quality=$videoQuality"
        }

        val streamUri = Uri.parse(targetStreamUrl)
        val artworkUri = track.artworkUrl?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

        val extras = Bundle().apply {
            putString(EXTRA_TRACK_ID, track.id)
            putString(EXTRA_ARTIST_ID, track.artist.id)
            putString(EXTRA_MEDIA_TYPE, if (isVideo) "video" else "audio")
            putString(EXTRA_VIDEO_URL, videoEndpointUrl)
            putBoolean(EXTRA_VIDEO_AVAILABLE, track.isVideoAvailable)
            putString(EXTRA_VIDEO_QUALITY, videoQuality)
            track.genre?.let { putString(EXTRA_GENRE, it) }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist.name)
            .setAlbumTitle(track.album?.name ?: "Single")
            .setArtworkUri(artworkUri)
            .setExtras(extras)
            .setIsPlayable(true)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(streamUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(streamUri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .build()
    }

    fun fromMediaItem(mediaItem: MediaItem): Track {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        val trackId = extras?.getString(EXTRA_TRACK_ID) ?: mediaItem.mediaId
        val artistId = extras?.getString(EXTRA_ARTIST_ID) ?: "unknown"
        val genre = extras?.getString(EXTRA_GENRE)
        val mediaTypeStr = extras?.getString(EXTRA_MEDIA_TYPE) ?: "audio"
        val videoUrl = extras?.getString(EXTRA_VIDEO_URL)
        val isVideoAvailable = extras?.getBoolean(EXTRA_VIDEO_AVAILABLE, true) ?: true
        val streamUrl = mediaItem.requestMetadata.mediaUri?.toString()
            ?: mediaItem.localConfiguration?.uri?.toString()

        val mediaType = if (mediaTypeStr.equals("video", ignoreCase = true) || streamUrl?.contains("type=video") == true) {
            MediaType.VIDEO
        } else {
            MediaType.AUDIO
        }

        return Track(
            id = trackId,
            title = metadata.title?.toString() ?: "Unknown Title",
            artist = Artist(
                id = artistId,
                name = metadata.artist?.toString() ?: "Unknown Artist"
            ),
            artworkUrl = metadata.artworkUri?.toString(),
            streamUrl = streamUrl,
            videoUrl = videoUrl,
            mediaType = mediaType,
            isVideoAvailable = isVideoAvailable,
            availableVideoQualities = listOf("Auto", "1080p", "720p", "480p", "360p", "144p"),
            genre = genre
        )
    }
}


