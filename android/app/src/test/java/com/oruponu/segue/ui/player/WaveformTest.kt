package com.oruponu.segue.ui.player

import com.oruponu.segue.core.Visuals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformTest {
    private fun visuals(vararg points: Pair<Int, Int>) =
        Visuals(
            sampleRate = 44100,
            hop = 1024,
            frameCount = 0,
            bandCount = 0,
            spectrum = ByteArray(0),
            peakLeft = ByteArray(0),
            peakRight = ByteArray(0),
            clipBits = ByteArray(0),
            waveformRate = 10,
            waveformCount = points.size,
            waveform =
                points.flatMap { (min, max) -> listOf(min.toByte(), max.toByte()) }.toByteArray(),
        )

    @Test
    fun barsSampleWaveformPoints() {
        val bars = waveformBars(visuals(-127 to 127, 0 to 0, -64 to 63, 0 to 127), barCount = 2)
        assertArrayEquals(floatArrayOf(1f, 127 / 254f), bars, 1e-6f)
    }

    @Test
    fun moreBarsThanPointsRepeatPoints() {
        val bars = waveformBars(visuals(-64 to 63), barCount = 3)
        assertArrayEquals(FloatArray(3) { 127 / 254f }, bars, 1e-6f)
    }

    @Test
    fun emptyWaveformGivesFlatBars() {
        assertArrayEquals(FloatArray(3), waveformBars(visuals(), barCount = 3), 0f)
    }

    @Test
    fun fractionIsClamped() {
        assertEquals(0f, seekFraction(-5f, 100f))
        assertEquals(1f, seekFraction(150f, 100f))
        assertEquals(0.25f, seekFraction(25f, 100f))
    }
}
