package com.musync.app.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track

@Entity(tableName = "user_profile")
data class UserEntity(
    @PrimaryKey val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String?,
    val provider: String,
    val isAnonymous: Boolean = false,
    val lastLoginAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artistHandle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val streamUrl: String?,
    val genre: String?,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = Artist(id = artistId, name = artistName, handle = artistHandle),
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        genre = genre
    )

    companion object {
        fun fromTrack(track: Track) = FavoriteEntity(
            trackId = track.id,
            title = track.title,
            artistId = track.artist.id,
            artistName = track.artist.name,
            artistHandle = track.artist.handle,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.streamUrl,
            genre = track.genre
        )
    }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["trackId"])]
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artistHandle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val streamUrl: String?,
    val genre: String?,
    val position: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = Artist(id = artistId, name = artistName, handle = artistHandle),
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        genre = genre
    )

    companion object {
        fun fromTrack(playlistId: Long, track: Track, position: Int) = PlaylistItemEntity(
            playlistId = playlistId,
            trackId = track.id,
            title = track.title,
            artistId = track.artist.id,
            artistName = track.artist.name,
            artistHandle = track.artist.handle,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.streamUrl,
            genre = track.genre,
            position = position
        )
    }
}

@Entity(tableName = "recently_played")
data class RecentlyPlayedEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artistHandle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val streamUrl: String?,
    val genre: String?,
    val playedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = Artist(id = artistId, name = artistName, handle = artistHandle),
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        genre = genre
    )

    companion object {
        fun fromTrack(track: Track) = RecentlyPlayedEntity(
            trackId = track.id,
            title = track.title,
            artistId = track.artist.id,
            artistName = track.artist.name,
            artistHandle = track.artist.handle,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.streamUrl,
            genre = track.genre,
            playedAt = System.currentTimeMillis()
        )
    }
}

@Entity(tableName = "cached_tracks")
data class CachedTrackEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val artistHandle: String?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val streamUrl: String?,
    val genre: String?,
    val playCount: Long?,
    val cachedAt: Long = System.currentTimeMillis()
) {
    fun toTrack() = Track(
        id = trackId,
        title = title,
        artist = Artist(id = artistId, name = artistName, handle = artistHandle),
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = streamUrl,
        genre = genre,
        playCount = playCount
    )

    companion object {
        fun fromTrack(track: Track) = CachedTrackEntity(
            trackId = track.id,
            title = track.title,
            artistId = track.artist.id,
            artistName = track.artist.name,
            artistHandle = track.artist.handle,
            artworkUrl = track.artworkUrl,
            durationMs = track.durationMs,
            streamUrl = track.streamUrl,
            genre = track.genre,
            playCount = track.playCount
        )
    }
}

