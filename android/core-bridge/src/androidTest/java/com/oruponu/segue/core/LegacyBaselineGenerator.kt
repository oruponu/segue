package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Writes the legacy implementation's analysis results to expected.json. Skipped in normal test
 * runs. See core/testdata/README.md for how to run it.
 */
@RunWith(AndroidJUnit4::class)
class LegacyBaselineGenerator {
    @Test
    fun generate() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("generateBaseline") == "true")
        LegacyBaseline.nativeInit()
        val modelPath = TestFixtures.model().absolutePath
        val fixtures = JSONObject()

        for (fixture in TestFixtures.MAIN_FIXTURES) {
            val path = TestFixtures.file(fixture.name).absolutePath
            val analyze = LegacyBaseline.nativeAnalyze(path)
            val style = LegacyBaseline.nativeStyle(path, modelPath)
            val spectrum = LegacyBaseline.nativeSpectrum(path)
            val peaks = LegacyBaseline.nativePeaks(path)
            assertEquals("${fixture.name} analyze", 0f, analyze[5])
            assertEquals("${fixture.name} style", 0f, style[0])
            assertEquals("${fixture.name} spectrum", 0f, spectrum[0])
            assertEquals("${fixture.name} peaks", 0f, peaks[0])

            val spectrumFrames = spectrum[1].toInt()
            val bandCount = spectrum[2].toInt()
            val peakFrames = peaks[1].toInt()
            val indices =
                listOf(0, peakFrames / 4, peakFrames / 2, peakFrames * 3 / 4, peakFrames - 1)
                    .distinct()

            val styles = JSONArray()
            for (i in 0 until style[1].toInt()) {
                styles.put(
                    JSONObject()
                        .put("index", style[2 + i].toInt())
                        .put("confidence", style[7 + i].toDouble()),
                )
            }
            val spectrumDb = JSONArray()
            val peakLeftDb = JSONArray()
            val peakRightDb = JSONArray()
            val clipFlags = JSONArray()
            for (f in indices) {
                val row = JSONArray()
                for (b in 0 until bandCount) row.put(spectrum[3 + f * bandCount + b].toDouble())
                spectrumDb.put(row)
                peakLeftDb.put(peaks[2 + f].toDouble())
                peakRightDb.put(peaks[2 + peakFrames + f].toDouble())
                clipFlags.put(peaks[2 + 2 * peakFrames + f].toInt())
            }
            var clipTotal = 0
            for (f in 0 until peakFrames) if (peaks[2 + 2 * peakFrames + f] != 0f) clipTotal++

            fixtures.put(
                fixture.name,
                JSONObject()
                    .put("source_sample_rate", fixture.sourceSampleRate)
                    .put("bpm", analyze[0].toDouble())
                    .put("bpm_confidence", analyze[1].toDouble())
                    .put("key_note", analyze[2].toInt())
                    .put("key_scale", analyze[3].toInt())
                    .put("key_confidence", analyze[4].toDouble())
                    .put("styles", styles)
                    .put("frames", JSONArray(indices))
                    .put("spectrum_db", spectrumDb)
                    .put("peak_left_db", peakLeftDb)
                    .put("peak_right_db", peakRightDb)
                    .put("clip_flags", clipFlags)
                    .put("clip_frames_total", clipTotal)
                    .put("legacy_spectrum_frames", spectrumFrames)
                    .put("legacy_peak_frames", peakFrames),
            )
        }

        val root =
            JSONObject()
                .put(
                    "generator",
                    "LegacyBaselineGenerator (core/legacy = the Flutter app's native implementation)",
                )
                .put("fixtures", fixtures)
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "expected.json")
            .writeText(root.toString(2))
    }
}
