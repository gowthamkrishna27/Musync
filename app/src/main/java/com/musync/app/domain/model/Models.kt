package com.musync.app.domain.model

enum class MediaType {
    AUDIO,
    VIDEO
}

data class Track(
    val id: String,
    val title: String,
    val artist: Artist,
    val album: Album? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    val streamUrl: String? = null,
    val videoUrl: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val isVideoAvailable: Boolean = true,
    val availableVideoQualities: List<String> = emptyList(),
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
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffle: Boolean = false,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = -1,
    val errorMessage: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val isVideoMode: Boolean = false,
    val videoQuality: String = "auto",
    val availableVideoQualities: List<String> = listOf("Auto", "1080p", "720p", "480p", "360p", "144p")
)


