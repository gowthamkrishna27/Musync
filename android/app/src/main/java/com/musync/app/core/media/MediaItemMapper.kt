package com.musync.app.core.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track

object MediaItemMapper {

    private const val EXTRA_TRACK_ID = "musync_extra_track_id"
    private const val EXTRA_ARTIST_ID = "musync_extra_artist_id"
    private const val EXTRA_GENRE = "musync_extra_genre"
    private const val DEFAULT_BASE_URL = "https://musync-8482.onrender.com"

    fun toMediaItem(
        track: Track,
        quality: String = "low",
        baseUrl: String = DEFAULT_BASE_URL
    ): MediaItem {
        val cleanId = track.id.removePrefix("yt_")

        val targetStreamUrl = if (track.id.startsWith("content://") || track.id.startsWith("file://")) {
            track.id
        } else if (!track.streamUrl.isNullOrBlank() && !track.streamUrl.contains("/stream?id=")) {
            track.streamUrl
        } else if (!track.streamUrl.isNullOrBlank() && track.streamUrl.contains("/stream?id=")) {
            if (track.streamUrl.contains("&quality=")) track.streamUrl else "${track.streamUrl}&quality=$quality"
        } else {
            val safeBase = if (baseUrl.isNotBlank()) baseUrl.trimEnd('/') else DEFAULT_BASE_URL
            "$safeBase/stream?id=$cleanId&quality=$quality"
        }

        val streamUri = Uri.parse(targetStreamUrl)
        val hqArtworkUrl = com.musync.app.core.image.ImageQualityHelper.getHighQualityArtworkUrl(track.artworkUrl, track.id)
        val artworkUri = hqArtworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }

        val extras = Bundle().apply {
            putString(EXTRA_TRACK_ID, track.id)
            putString(EXTRA_ARTIST_ID, track.artist.id)
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
        val streamUrl = mediaItem.requestMetadata.mediaUri?.toString()
            ?: mediaItem.localConfiguration?.uri?.toString()

        return Track(
            id = trackId,
            title = metadata.title?.toString() ?: "Unknown Title",
            artist = Artist(
                id = artistId,
                name = metadata.artist?.toString() ?: "Unknown Artist"
            ),
            artworkUrl = metadata.artworkUri?.toString(),
            streamUrl = streamUrl,
            genre = genre
        )
    }
}
