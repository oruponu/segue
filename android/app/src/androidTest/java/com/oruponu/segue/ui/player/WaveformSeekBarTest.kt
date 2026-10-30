package com.oruponu.segue.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oruponu.segue.core.Visuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TAG = "seekBar"

@RunWith(AndroidJUnit4::class)
class WaveformSeekBarTest {
    @get:Rule val compose = createComposeRule()

    private val seeks = mutableListOf<Long>()

    private val visuals =
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
            waveformCount = 100,
            waveform = ByteArray(200) { if (it % 2 == 0) -64 else 63 },
        )

    private fun setContent(visuals: Visuals?, durationMs: Long) {
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                WaveformSeekBar(
                    visuals = visuals,
                    positionMs = 0,
                    durationMs = durationMs,
                    onSeek = { seeks += it },
                    modifier = Modifier.testTag(TAG),
                )
            }
        }
    }

    private fun assertNear(expected: Long, actual: Long, tolerance: Double) {
        assertTrue(
            "$actual is not near $expected",
            kotlin.math.abs(actual - expected) <= expected * tolerance,
        )
    }

    @Test
    fun tapSeeksImmediately() {
        setContent(visuals, durationMs = 200_000)
        compose.onNodeWithTag(TAG).performTouchInput { click(Offset(width / 2f, 30.dp.toPx())) }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertNear(100_000, seeks.single(), 0.01)
        }
    }

    @Test
    fun dragSeeksOnceOnRelease() {
        setContent(visuals, durationMs = 200_000)
        compose.onNodeWithTag(TAG).performTouchInput {
            val y = 30.dp.toPx()
            down(Offset(1f, y))
            moveTo(Offset(width / 4f, y))
            moveTo(Offset(width * 3 / 4f, y))
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }

        compose.onNodeWithTag(TAG).performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertNear(150_000, seeks.single(), 0.02)
        }
    }

    @Test
    fun unknownDurationIgnoresSeek() {
        setContent(visuals, durationMs = 0)
        compose.onNodeWithTag(TAG).performTouchInput { click(Offset(width / 2f, 30.dp.toPx())) }
        compose.onNodeWithTag(TAG).performTouchInput {
            val y = 30.dp.toPx()
            down(Offset(1f, y))
            moveTo(Offset(width * 3 / 4f, y))
            up()
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
    }

    @Test
    fun showsLineWithoutWaveform() {
        setContent(visuals = null, durationMs = 200_000)
        compose.onNodeWithText("0:00").assertExists()
        compose.onNodeWithText("3:20").assertExists()
    }
}
