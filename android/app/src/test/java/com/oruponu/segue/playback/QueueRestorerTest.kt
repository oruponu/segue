package com.oruponu.segue.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRestorerTest {
    @Test
    fun currentTrackRemainsKeepsPosition() {
        val saved = SavedPlayback(listOf("a", "b", "c"), currentKey = "b", positionMs = 5000)
        assertEquals(
            RestoredQueue(listOf("a", "b", "c"), 1, 5000),
            restoreQueue(saved, setOf("a", "b", "c")),
        )
    }

    @Test
    fun removedTracksAreDroppedFromQueue() {
        val saved = SavedPlayback(listOf("a", "b", "c"), currentKey = "c", positionMs = 3000)
        assertEquals(RestoredQueue(listOf("a", "c"), 1, 3000), restoreQueue(saved, setOf("a", "c")))
    }

    @Test
    fun removedCurrentUsesNextSurvivor() {
        val saved = SavedPlayback(listOf("a", "b", "c", "d"), currentKey = "b", positionMs = 5000)
        assertEquals(RestoredQueue(listOf("a", "d"), 1, 0), restoreQueue(saved, setOf("a", "d")))
    }

    @Test
    fun removedCurrentWithoutNextUsesPreviousSurvivor() {
        val saved = SavedPlayback(listOf("a", "b", "c"), currentKey = "c", positionMs = 5000)
        assertEquals(RestoredQueue(listOf("a"), 0, 0), restoreQueue(saved, setOf("a")))
    }

    @Test
    fun noSurvivorsRestoresNothing() {
        val saved = SavedPlayback(listOf("a", "b"), currentKey = "a", positionMs = 5000)
        assertNull(restoreQueue(saved, emptySet()))
        assertNull(restoreQueue(SavedPlayback(), setOf("a")))
    }
}
