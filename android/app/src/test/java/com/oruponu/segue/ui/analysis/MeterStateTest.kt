package com.oruponu.segue.ui.analysis

import com.oruponu.segue.core.Visuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterStateTest {
    // sampleRate 1000 and hop 1000 make one frame per second; one band.
    private fun visuals(vararg levels: Int, clipped: Set<Int> = emptySet()): Visuals {
        val bytes = ByteArray(levels.size) { levels[it].toByte() }
        val clipBits = ByteArray((levels.size + 7) / 8)
        clipped.forEach {
            clipBits[it / 8] = (clipBits[it / 8].toInt() or (1 shl (it % 8))).toByte()
        }
        return Visuals(
            sampleRate = 1000,
            hop = 1000,
            frameCount = levels.size,
            bandCount = 1,
            spectrum = bytes,
            peakLeft = bytes,
            peakRight = bytes,
            clipBits = clipBits,
            waveformRate = 1,
            waveformCount = 1,
            waveform = ByteArray(2),
        )
    }

    private fun target(db: Float, clipped: Boolean = false) =
        MeterTarget(floatArrayOf(db), db, db, clipped)

    private fun MeterState.step(times: Int, target: MeterTarget?) {
        repeat(times) { update(target, 0.1f) }
    }

    @Test
    fun interpolatesBetweenFrames() {
        val target = visuals(0, 255).meterTargetAt(500)
        assertEquals(-25f, target.spectrumDb[0], 0.1f)
        assertEquals(-25f, target.peakLeftDb, 0.1f)
        assertEquals(-25f, target.peakRightDb, 0.1f)
    }

    @Test
    fun positionPastEndUsesLastFrame() {
        val target = visuals(0, 100, 200).meterTargetAt(600_000)
        assertEquals(Visuals.levelToDb(200), target.spectrumDb[0], 1e-4f)
        assertEquals(Visuals.levelToDb(200), target.peakLeftDb, 1e-4f)
    }

    @Test
    fun singleFrameVisuals() {
        val single = visuals(100)
        assertEquals(Visuals.levelToDb(100), single.meterTargetAt(0).spectrumDb[0], 1e-4f)
        assertEquals(Visuals.levelToDb(100), single.meterTargetAt(5000).spectrumDb[0], 1e-4f)
    }

    @Test
    fun clipComesFromFloorFrame() {
        val clips = visuals(0, 0, 0, clipped = setOf(1))
        assertFalse(clips.meterTargetAt(500).clipped)
        assertTrue(clips.meterTargetAt(1200).clipped)
    }

    @Test
    fun spectrumRisesAtOnceAndDecaysAt25() {
        val state = MeterState(1)
        state.update(target(-10f), 1 / 60f)
        assertEquals(-10f, state.spectrumDb[0], 1e-4f)
        state.update(target(-50f), 0.1f)
        assertEquals(-12.5f, state.spectrumDb[0], 1e-4f)
    }

    @Test
    fun peakBarDecaysAt26() {
        val state = MeterState(1)
        state.update(target(-10f), 1 / 60f)
        state.update(target(-50f), 0.1f)
        assertEquals(-12.6f, state.peakLeftDb, 1e-4f)
        assertEquals(-12.6f, state.peakRightDb, 1e-4f)
    }

    @Test
    fun peakHoldWaitsOneSecondThenDecaysAt10() {
        val state = MeterState(1)
        state.update(target(-10f), 0.1f)
        assertEquals(-10f, state.holdLeftDb, 1e-4f)
        state.step(5, target(-50f))
        assertEquals(-10f, state.holdLeftDb, 1e-4f)
        state.step(15, target(-50f))
        assertEquals(-20f, state.holdLeftDb, 1f)
        assertEquals(-20f, state.holdRightDb, 1f)
    }

    @Test
    fun clipStaysLitForOneSecond() {
        val state = MeterState(1)
        state.update(target(-10f, clipped = true), 0.1f)
        state.step(5, target(-10f))
        assertTrue(state.clipping)
        state.step(6, target(-10f))
        assertFalse(state.clipping)
    }

    @Test
    fun dtIsClamped() {
        val state = MeterState(1)
        state.update(target(-10f), 1 / 60f)
        state.update(target(-50f), 1f)
        assertEquals(-12.5f, state.spectrumDb[0], 1e-4f)
    }

    @Test
    fun silenceResetsAll() {
        val idle = MeterState(1)
        idle.update(null, 1 / 60f)
        assertFalse(idle.isActive)

        val state = MeterState(1)
        state.update(target(-10f), 1 / 60f)
        assertTrue(state.isActive)
        var updates = 0
        while (state.isActive && updates < 1000) {
            state.update(null, 0.1f)
            updates++
        }
        assertFalse(state.isActive)
        assertEquals(FLOOR_DB, state.holdLeftDb, 0f)
        assertEquals(FLOOR_DB, state.peakLeftDb, 0f)
    }

    @Test
    fun holdOutlivesSpectrum() {
        val state = MeterState(1)
        state.update(target(-10f, clipped = true), 0.1f)
        state.step(17, null)
        assertEquals(FLOOR_DB, state.spectrumDb[0], 0f)
        assertTrue(state.isActive)
        assertTrue(state.holdLeftDb > FLOOR_DB)
        assertFalse(state.clipping)

        var updates = 0
        while (state.isActive && updates < 1000) {
            state.update(null, 0.1f)
            updates++
        }
        assertFalse(state.isActive)
        assertEquals(FLOOR_DB, state.holdLeftDb, 0f)
    }

    @Test
    fun peakMeterPositionBreakpoints() {
        assertEquals(0f, peakMeterPosition(-60f), 1e-6f)
        assertEquals(0f, peakMeterPosition(-50f), 1e-6f)
        assertEquals(0.075f, peakMeterPosition(-40f), 1e-6f)
        assertEquals(0.15f, peakMeterPosition(-30f), 1e-6f)
        assertEquals(0.30f, peakMeterPosition(-20f), 1e-6f)
        assertEquals(0.5f, peakMeterPosition(-10f), 1e-6f)
        assertEquals(0.75f, peakMeterPosition(-5f), 1e-6f)
        assertEquals(1f, peakMeterPosition(0f), 1e-6f)
        assertEquals(1f, peakMeterPosition(3f), 1e-6f)
    }
}
