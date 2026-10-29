package com.oruponu.segue.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class SpectrumMeterTest {
    @Test
    fun frequencyAxisIsLogarithmic() {
        assertEquals(0f, frequencyFraction(20f), 1e-6f)
        assertEquals(1f, frequencyFraction(20_000f), 1e-6f)
        assertEquals(1f / 3, frequencyFraction(200f), 1e-5f)
        assertEquals(2f / 3, frequencyFraction(2_000f), 1e-5f)
    }

    @Test
    fun frequencyOutsideTheAxisIsClamped() {
        assertEquals(0f, frequencyFraction(5f), 1e-6f)
        assertEquals(1f, frequencyFraction(40_000f), 1e-6f)
    }

    @Test
    fun levelFractionSpansFloorToZero() {
        assertEquals(0f, levelFraction(-60f), 1e-6f)
        assertEquals(0f, levelFraction(FLOOR_DB), 1e-6f)
        assertEquals(0.5f, levelFraction(-25f), 1e-6f)
        assertEquals(1f, levelFraction(0f), 1e-6f)
        assertEquals(1f, levelFraction(3f), 1e-6f)
    }
}
