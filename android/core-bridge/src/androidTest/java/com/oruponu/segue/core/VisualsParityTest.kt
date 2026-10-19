package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Checks that the visuals from SegueCore.decode match the legacy results (expected.json) within one quantization step. */
@RunWith(AndroidJUnit4::class)
class VisualsParityTest {
    @Test
    fun spectrumPeaksAndClipsMatchLegacy() {
        val fixtures = TestFixtures.expected().getJSONObject("fixtures")
        for (fixture in TestFixtures.MAIN_FIXTURES) {
            val exp = fixtures.getJSONObject(fixture.name)
            val cache = File(TestFixtures.workDir(), "parity.sgv")
            CancelToken().use { token ->
                SegueCore.decode(TestFixtures.file(fixture.name).absolutePath, token).use { audio ->
                    SegueCore.writeVisuals(audio, cache.absolutePath)
                }
            }
            val v = SegueCore.readVisuals(cache.absolutePath)
            val legacyPeakFrames = exp.getInt("legacy_peak_frames")
            assertTrue("${fixture.name} frameCount=${v.frameCount}", v.frameCount >= legacyPeakFrames)

            val frames = exp.getJSONArray("frames")
            for (i in 0 until frames.length()) {
                val f = frames.getInt(i)
                val row = exp.getJSONArray("spectrum_db").getJSONArray(i)
                for (b in 0 until v.bandCount) {
                    val expected = Visuals.quantizeDb(row.getDouble(b).toFloat())
                    val actual = v.spectrumLevel(f, b)
                    assertTrue("${fixture.name} spectrum f=$f b=$b expected=$expected actual=$actual",
                        abs(expected - actual) <= 1)
                }
                val expL = Visuals.quantizeDb(exp.getJSONArray("peak_left_db").getDouble(i).toFloat())
                val expR = Visuals.quantizeDb(exp.getJSONArray("peak_right_db").getDouble(i).toFloat())
                assertTrue("${fixture.name} peakL f=$f", abs(expL - v.peakLeftLevel(f)) <= 1)
                assertTrue("${fixture.name} peakR f=$f", abs(expR - v.peakRightLevel(f)) <= 1)
                if (fixture.sourceSampleRate == 44100) {
                    assertEquals("${fixture.name} clip f=$f",
                        exp.getJSONArray("clip_flags").getInt(i) != 0, v.isClipped(f))
                }
            }

            val clipTotal = (0 until legacyPeakFrames).count { v.isClipped(it) }
            val expectedClipTotal = exp.getInt("clip_frames_total")
            if (fixture.sourceSampleRate == 44100) {
                assertEquals("${fixture.name} clip total", expectedClipTotal, clipTotal)
            } else {
                assertTrue("${fixture.name} clip total $clipTotal vs $expectedClipTotal",
                    abs(expectedClipTotal - clipTotal) <= 2)
            }
        }
    }
}
