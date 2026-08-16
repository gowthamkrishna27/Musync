package com.musync.app.data.remote

import com.musync.app.domain.model.Album
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Playlist
import com.musync.app.domain.model.Track
import com.musync.app.domain.provider.MusicProvider

class MockMusicProvider : MusicProvider {
    override val providerId: String = "mock"
    override val displayName: String = "Mock Provider (Testing)"

    private val sampleArtist1 = Artist(id = "artist-1", name = "Luna Eclipse", handle = "lunaeclipse", imageUrl = null)
    private val sampleArtist2 = Artist(id = "artist-2", name = "Neon Pulse", handle = "neonpulse", imageUrl = null)

    private val sampleTracks = listOf(
        Track(
            id = "mock-1",
            title = "Midnight Horizon",
            artist = sampleArtist1,
            artworkUrl = null,
            durationMs = 210000L,
            streamUrl = "https://example.com/audio1.mp3",
            genre = "Electronic"
        ),
        Track(
            id = "mock-2",
            title = "Cybernetic Dreams",
            artist = sampleArtist2,
            artworkUrl = null,
            durationMs = 185000L,
            streamUrl = "https://example.com/audio2.mp3",
            genre = "Synthwave"
        ),
        Track(
            id = "mock-3",
            title = "Astral Echoes",
            artist = sampleArtist1,
            artworkUrl = null,
            durationMs = 240000L,
            streamUrl = "https://example.com/audio3.mp3",
            genre = "Ambient"
        )
    )

    override suspend fun testConnection(baseUrl: String?, apiKey: String?): Boolean = true

    override suspend fun search(query: String): List<Track> {
        return sampleTracks.filter { it.title.contains(query, ignoreCase = true) || it.artist.name.contains(query, ignoreCase = true) }
    }

    override suspend fun searchArtists(query: String): List<Artist> {
        return listOf(sampleArtist1, sampleArtist2).filter { it.name.contains(query, ignoreCase = true) }
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        return emptyList()
    }

    override suspend fun getTrending(): List<Track> = sampleTracks

    override suspend fun getUndergroundTrending(): List<Track> = sampleTracks.reversed()

    override suspend fun getTrack(id: String): Track? = sampleTracks.find { it.id == id }

    override suspend fun getArtist(id: String): Artist? = listOf(sampleArtist1, sampleArtist2).find { it.id == id }

    override suspend fun getArtistTracks(artistId: String): List<Track> = sampleTracks.filter { it.artist.id == artistId }

    override suspend fun getAlbum(id: String): Album? = null

    override suspend fun getPlaylist(id: String): Playlist? = null

    override suspend fun getStreamUrl(track: Track): String? = track.streamUrl
}

