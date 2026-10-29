package com.oruponu.segue.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLabelsTest {
    @Test
    fun speedLabels() {
        assertEquals("0.5x", speedLabel(0.5f))
        assertEquals("0.75x", speedLabel(0.75f))
        assertEquals("1.0x", speedLabel(1f))
        assertEquals("1.25x", speedLabel(1.25f))
        assertEquals("2.0x", speedLabel(2f))
    }

    @Test
    fun semitoneLabels() {
        assertEquals("0", semitoneLabel(0))
        assertEquals("+2", semitoneLabel(2))
        assertEquals("-3", semitoneLabel(-3))
    }
}
