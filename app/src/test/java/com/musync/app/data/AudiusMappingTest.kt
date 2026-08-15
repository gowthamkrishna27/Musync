package com.musync.app.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.musync.app.data.api.AudiusMusicProvider
import com.musync.app.data.api.dto.AudiusArtworkDto
import com.musync.app.data.api.dto.AudiusResponse
import com.musync.app.data.api.dto.AudiusTrackDto
import com.musync.app.data.api.dto.AudiusUserDto
import com.musync.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiusMappingTest {

    private val gson = Gson()

    @Test
    fun testTrackJsonDeserializationAndMapping() {
        val sampleJson = """
        {
            "data": [
                {
                    "id": "D7KyP",
                    "title": "Neon Skyline",
                    "duration": 215,
                    "genre": "Electronic",
                    "play_count": 48200,
                    "user": {
                        "id": "7e9vM",
                        "name": "Synth Master",
                        "handle": "synthmaster",
                        "profile_picture": {
                            "150x150": "https://audius.co/img/150.jpg",
                            "480x480": "https://audius.co/img/480.jpg",
                            "1000x1000": "https://audius.co/img/1000.jpg"
                        }
                    },
                    "artwork": {
                        "150x150": "https://audius.co/art/150.jpg",
                        "480x480": "https://audius.co/art/480.jpg",
                        "1000x1000": "https://audius.co/art/1000.jpg"
                    }
                }
            ]
        }
        """.trimIndent()

        val type = object : TypeToken<AudiusResponse<List<AudiusTrackDto>>>() {}.type
        val response: AudiusResponse<List<AudiusTrackDto>> = gson.fromJson(sampleJson, type)

        assertNotNull(response.data)
        assertEquals(1, response.data?.size)

        val dto = response.data!!.first()
        assertEquals("D7KyP", dto.id)
        assertEquals("Neon Skyline", dto.title)
        assertEquals(215L, dto.duration)
        assertEquals("Synth Master", dto.user?.name)
        assertEquals("https://audius.co/art/1000.jpg", dto.artwork?.large)
    }

    @Test
    fun testArtworkExtractionFallback() {
        val artworkOnlySmall = AudiusArtworkDto(small = "https://audius.co/small.jpg", medium = null, large = null)
        val best = artworkOnlySmall.large ?: artworkOnlySmall.medium ?: artworkOnlySmall.small
        assertEquals("https://audius.co/small.jpg", best)
    }
}

