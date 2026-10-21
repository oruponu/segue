package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the BPM, key and top style match the legacy results (expected.json). */
@RunWith(AndroidJUnit4::class)
class MusicParityTest {
    @Test
    fun bpmKeyAndTopStyleMatchLegacy() {
        val fixtures = TestFixtures.expected().getJSONObject("fixtures")
        SegueCore.loadModels(TestFixtures.model().absolutePath).use { models ->
            for (fixture in TestFixtures.MAIN_FIXTURES) {
                val exp = fixtures.getJSONObject(fixture.name)
                val result =
                    CancelToken().use { token ->
                        SegueCore.decode(TestFixtures.file(fixture.name).absolutePath, token).use {
                            audio ->
                            SegueCore.analyze(audio, models, token)
                        }
                    }
                val expectedBpm = exp.getDouble("bpm").toFloat()
                assertTrue(
                    "${fixture.name} bpm ${result.bpm} vs $expectedBpm",
                    abs(result.bpm - expectedBpm) <= 0.5f,
                )
                assertEquals("${fixture.name} key note", exp.getInt("key_note"), result.keyNote)
                assertEquals("${fixture.name} key scale", exp.getInt("key_scale"), result.keyScale)
                val expectedTop = exp.getJSONArray("styles").getJSONObject(0)
                assertEquals(
                    "${fixture.name} top style",
                    expectedTop.getInt("index"),
                    result.styles[0].labelIndex,
                )
                val expectedConfidence = expectedTop.getDouble("confidence").toFloat()
                assertTrue(
                    "${fixture.name} top confidence ${result.styles[0].confidence} vs $expectedConfidence",
                    abs(result.styles[0].confidence - expectedConfidence) <= 0.02f,
                )
            }
        }
    }
}
