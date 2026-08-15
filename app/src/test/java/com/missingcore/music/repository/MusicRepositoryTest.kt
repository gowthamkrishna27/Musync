package com.missingcore.music.repository

import com.missingcore.music.data.api.MockMusicProvider
import com.missingcore.music.domain.model.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryTest {

    private val mockProvider = MockMusicProvider()

    @Test
    fun testMockProviderTrending() = runBlocking {
        val tracks = mockProvider.getTrending()
        assertEquals(3, tracks.size)
        assertEquals("Midnight Horizon", tracks[0].title)
    }

    @Test
    fun testMockProviderSearch() = runBlocking {
        val results = mockProvider.search("Cybernetic")
        assertEquals(1, results.size)
        assertEquals("mock-2", results[0].id)
    }

    @Test
    fun testMockProviderStreamUrl() = runBlocking {
        val track = mockProvider.getTrack("mock-1")
        assertNotNull(track)
        val streamUrl = mockProvider.getStreamUrl(track!!)
        assertEquals("https://example.com/audio1.mp3", streamUrl)
    }
}
