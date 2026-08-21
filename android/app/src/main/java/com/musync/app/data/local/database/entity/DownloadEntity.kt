package com.musync.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val artistId: String,
    val albumName: String? = null,
    val albumId: String? = null,
    val artworkUrl: String? = null,
    val localArtworkPath: String? = null,
    val localFilePath: String,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val format: String = "mp4",
    val downloadedAt: Long = System.currentTimeMillis(),
    val status: String = "COMPLETED", // QUEUED, DOWNLOADING, COMPLETED, PAUSED, FAILED
    val progress: Float = 1.0f,
    val errorMessage: String? = null
) {
    fun toTrack(): Track {
        val artistObj = Artist(id = artistId, name = artistName)
        val albumObj = albumName?.let {
            Album(id = albumId ?: "album_${it.hashCode()}", name = it, artist = artistObj, artworkUrl = artworkUrl)
        }
        return Track(
            id = id,
            title = title,
            artist = artistObj,
            album = albumObj,
            artworkUrl = localArtworkPath?.takeIf { it.isNotBlank() } ?: artworkUrl,
            durationMs = durationMs,
            streamUrl = "file://$localFilePath",
            genre = "Offline",
            explicit = false
        )
    }
}
