package com.missingcore.music.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: Artist,
    val album: Album? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val streamUrl: String? = null,
    val genre: String? = null,
    val playCount: Long? = null,
    val explicit: Boolean = false
)

data class Artist(
    val id: String,
    val name: String,
    val handle: String? = null,
    val imageUrl: String? = null,
    val bio: String? = null,
    val trackCount: Int? = null
)

data class Album(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val artist: Artist? = null,
    val releaseDate: String? = null,
    val trackCount: Int? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val artworkUrl: String? = null,
    val tracks: List<Track> = emptyList(),
    val isCustom: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val errorMessage: String? = null
)
