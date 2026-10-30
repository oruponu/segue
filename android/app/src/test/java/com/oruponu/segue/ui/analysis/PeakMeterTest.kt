package com.oruponu.segue.ui.analysis

import org.junit.Assert.assertEquals
import org.junit.Test

class PeakMeterTest {
    @Test
    fun holdLabelShowsOneDecimal() {
        assertEquals("-12.3", holdLabel(-12.34f))
        assertEquals("0.0", holdLabel(0f))
        assertEquals("-49.9", holdLabel(-49.9f))
    }

    @Test
    fun holdLabelAtTheFloorIsInfinity() {
        assertEquals("-inf", holdLabel(FLOOR_DB))
        assertEquals("-inf", holdLabel(-60f))
    }
}
