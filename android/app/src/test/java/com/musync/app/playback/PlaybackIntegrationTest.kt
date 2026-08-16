package com.musync.app.playback

import com.musync.app.data.remote.YouTubeMusicProvider
import com.musync.app.domain.model.Artist
import com.musync.app.domain.model.Track
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class PlaybackIntegrationTest {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Test
    fun testEndToEndPlaybackPipeline() = runBlocking {
        // Step 1: Initialize Provider pointing to local server
        val provider = YouTubeMusicProvider()
        provider.updateConfiguration("http://127.0.0.1:5000", null)

        // Step 2: Search for real song
        val searchResults = provider.search("Starboy")
        println("→ Step 2 [Search]: Found ${searchResults.size} tracks")
        assertTrue("Search results should not be empty", searchResults.isNotEmpty())

        val selectedTrack = searchResults.first()
        println("→ Step 2 [Selected Track]: '${selectedTrack.title}' by '${selectedTrack.artist.name}' (ID: ${selectedTrack.id})")
        assertNotNull(selectedTrack.streamUrl)
        println("→ Step 2 [Stream URL]: ${selectedTrack.streamUrl}")

        // Step 3: Test HTTP GET on the audio stream endpoint (Range: bytes=0-1024)
        val audioUrl = selectedTrack.streamUrl!!
        val req = Request.Builder()
            .url(audioUrl)
            .header("Range", "bytes=0-1024")
            .build()

        val resp = httpClient.newCall(req).execute()
        val statusCode = resp.code
        val contentType = resp.header("Content-Type")
        val contentRange = resp.header("Content-Range")
        val acceptRanges = resp.header("Accept-Ranges")
        val bodyBytes = resp.body?.bytes() ?: ByteArray(0)

        println("→ Step 3 [HTTP Status]: $statusCode")
        println("→ Step 3 [Content-Type]: $contentType")
        println("→ Step 3 [Content-Range]: $contentRange")
        println("→ Step 3 [Accept-Ranges]: $acceptRanges")
        println("→ Step 3 [Bytes Received]: ${bodyBytes.size}")

        assertTrue("Status must be 200 or 206", statusCode == 200 || statusCode == 206)
        assertTrue("Content-Type must be audio format", contentType?.contains("audio") == true || contentType?.contains("webm") == true || contentType?.contains("mp4") == true)
        assertTrue("Must receive audio chunk bytes", bodyBytes.size > 500)

        // Step 4: Validate Track Model schema & stream resolution
        assertEquals(selectedTrack.id, "yt_3_g2un5M350")
        assertTrue("Stream URL must target active streaming gateway", audioUrl.contains("/stream?id=3_g2un5M350"))
        assertEquals("Starboy (feat. Daft Punk)", selectedTrack.title)
        assertEquals("The Weeknd", selectedTrack.artist.name)

        println("✓ [EXOPLAYER PIPELINE VERIFIED]: Real audio bytes received (${bodyBytes.size} bytes), Content-Type: $contentType, Range: $contentRange, Status: $statusCode")
    }
}

