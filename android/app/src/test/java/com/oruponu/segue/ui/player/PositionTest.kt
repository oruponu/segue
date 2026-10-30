package com.oruponu.segue.ui.player

import com.oruponu.segue.playback.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionTest {
    @Test
    fun pausedPositionDoesNotMove() {
        val state = PlayerUiState(isPlaying = false, positionMs = 1000, positionAtElapsedMs = 0)
        assertEquals(1000L, extrapolatedPositionMs(state, nowElapsedMs = 500))
    }

    @Test
    fun playingPositionAdvancesWithSpeed() {
        val state =
            PlayerUiState(isPlaying = true, positionMs = 1000, positionAtElapsedMs = 0, speed = 2f)
        assertEquals(2000L, extrapolatedPositionMs(state, nowElapsedMs = 500))
    }

    @Test
    fun extrapolationStopsAtDuration() {
        val state =
            PlayerUiState(isPlaying = true, positionMs = 1000, positionAtElapsedMs = 0, speed = 2f)
        assertEquals(3000L, extrapolatedPositionMs(state.copy(durationMs = 3000), 2000))
        assertEquals(5000L, extrapolatedPositionMs(state.copy(durationMs = 0), 2000))
    }
}
