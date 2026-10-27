package com.oruponu.segue.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackParamsTest {
    @Test
    fun pitchRatioMatchesSemitones() {
        assertEquals(2f, pitchRatio(12))
        assertEquals(0.5f, pitchRatio(-12))
        assertEquals(1f, pitchRatio(0))
        assertEquals(5, semitonesOf(pitchRatio(5)))
    }

    @Test
    fun repeatCyclesOffAllOne() {
        assertEquals(Player.REPEAT_MODE_ALL, nextRepeatMode(Player.REPEAT_MODE_OFF))
        assertEquals(Player.REPEAT_MODE_ONE, nextRepeatMode(Player.REPEAT_MODE_ALL))
        assertEquals(Player.REPEAT_MODE_OFF, nextRepeatMode(Player.REPEAT_MODE_ONE))
    }
}
