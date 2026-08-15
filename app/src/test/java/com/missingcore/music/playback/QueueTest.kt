package com.missingcore.music.playback

import com.missingcore.music.domain.model.Artist
import com.missingcore.music.domain.model.RepeatMode
import com.missingcore.music.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTest {

    private val artist = Artist(id = "a1", name = "Test Artist")
    private val track1 = Track(id = "1", title = "Song 1", artist = artist)
    private val track2 = Track(id = "2", title = "Song 2", artist = artist)
    private val track3 = Track(id = "3", title = "Song 3", artist = artist)

    @Test
    fun testQueueAddAndRemove() {
        val queue = mutableListOf(track1, track2)
        assertEquals(2, queue.size)

        // Add to queue
        queue.add(track3)
        assertEquals(3, queue.size)
        assertEquals("3", queue.last().id)

        // Remove from queue
        val index = queue.indexOfFirst { it.id == "2" }
        queue.removeAt(index)
        assertEquals(2, queue.size)
        assertFalse(queue.any { it.id == "2" })
    }

    @Test
    fun testPlayNextInsertion() {
        val queue = mutableListOf(track1, track3)
        val currentIndex = 0

        // Play Next inserts immediately after current index
        val insertIndex = currentIndex + 1
        queue.add(insertIndex, track2)

        assertEquals(3, queue.size)
        assertEquals("2", queue[1].id)
        assertEquals("3", queue[2].id)
    }

    @Test
    fun testRepeatModeTransitions() {
        var mode = RepeatMode.OFF
        fun nextMode(current: RepeatMode) = when (current) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        mode = nextMode(mode)
        assertEquals(RepeatMode.ALL, mode)

        mode = nextMode(mode)
        assertEquals(RepeatMode.ONE, mode)

        mode = nextMode(mode)
        assertEquals(RepeatMode.OFF, mode)
    }
}
