package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacySmokeTest {
    @Test
    fun legacyAnalyzeAndStyleSucceedOnWavFixture() {
        LegacyBaseline.nativeInit()
        val path = TestFixtures.file("click120_aminor_44k_stereo.wav").absolutePath

        val analyze = LegacyBaseline.nativeAnalyze(path)
        assertEquals("errorCode", 0f, analyze[5])
        assertTrue("bpm", analyze[0] > 0f)

        val style = LegacyBaseline.nativeStyle(path, TestFixtures.model().absolutePath)
        assertEquals("errorCode", 0f, style[0])
        assertEquals("count", 5f, style[1])
    }
}
