package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.log10

@RunWith(AndroidJUnit4::class)
class DecodeTest {
    private fun path(name: String) = TestFixtures.file(name).absolutePath

    private fun decodeVisuals(path: String): Visuals {
        val cache = File(TestFixtures.workDir(), "decode-test.sgv")
        CancelToken().use { token ->
            SegueCore.decode(path, token).use { audio -> SegueCore.writeVisuals(audio, cache.absolutePath) }
        }
        return SegueCore.readVisuals(cache.absolutePath)
    }

    private fun assertDecodeFails(path: String) {
        val e = assertThrows(SegueException::class.java) {
            CancelToken().use { token -> SegueCore.decode(path, token).close() }
        }
        assertEquals(SegueStatus.DECODE_FAILED, e.status)
    }

    @Test
    fun wavFixtureHasDefinedFrameCounts() {
        val v = decodeVisuals(path("click120_aminor_44k_stereo.wav"))
        assertEquals(44100, v.sampleRate)
        assertEquals(1024, v.hop)
        assertEquals(32, v.bandCount)
        assertEquals(100, v.waveformRate)
        assertEquals(1292, v.frameCount) // ceil(1,323,000 / 1024)
        assertEquals(3000, v.waveformCount) // ceil(1,323,000 / 441)
        assertEquals(1292 * 32, v.spectrum.size)
        assertEquals(1292, v.peakLeft.size)
        assertEquals((1292 + 7) / 8, v.clipBits.size)
        assertEquals(3000 * 2, v.waveform.size)
        for (p in 0 until v.waveformCount) assertTrue("point $p", v.waveformMin(p) <= v.waveformMax(p))
    }

    @Test
    fun shorterThanOneSpectrumFrameStillHasFrames() {
        val v = decodeVisuals(path("tiny_2000_mono.wav"))
        assertEquals(2, v.frameCount) // ceil(2000 / 1024)
        assertEquals(5, v.waveformCount) // ceil(2000 / 441)
    }

    @Test
    fun lengthNotDivisibleByHopRoundsUp() {
        val v = decodeVisuals(path("odd_length_stereo.wav"))
        assertEquals(101, v.frameCount) // ceil(102,917 / 1024)
        assertEquals(234, v.waveformCount) // ceil(102,917 / 441)
    }

    @Test
    fun monoSourceKeepsItsLevel() {
        // tiny_2000_mono.wav is a sine wave with amplitude 0.5 (-6.02dB). Copying it to L/R must not
        // lower it by 3dB
        val v = decodeVisuals(path("tiny_2000_mono.wav"))
        val expected = Visuals.quantizeDb(20f * log10(0.5f))
        assertTrue("left=${v.peakLeftLevel(0)} expected=$expected", abs(v.peakLeftLevel(0) - expected) <= 1)
        assertEquals(v.peakLeftLevel(0), v.peakRightLevel(0))
    }

    @Test
    fun corruptedMiddleIsSkippedAndDecodingContinues() {
        val v = decodeVisuals(path("corrupt_middle.mp3"))
        // The original mp3 has about 1292 frames. It gets shorter by at most the 4096 corrupted bytes
        // (about 0.2 seconds)
        assertTrue("frameCount=${v.frameCount}", v.frameCount in 1100..1310)
    }

    @Test
    fun monoSourceHasIdenticalLeftAndRightPeaks() {
        val v = decodeVisuals(path("click100_dminor_44k_mono.m4a"))
        assertArrayEquals(v.peakLeft, v.peakRight)
        assertTrue(v.peakLeft.any { (it.toInt() and 0xFF) > 0 })
    }

    @Test
    fun surround51IsDownmixedToStereo() {
        val v = decodeVisuals(path("surround51_44k.flac"))
        assertEquals(431, v.frameCount) // ceil(441,000 / 1024)
        assertEquals(1000, v.waveformCount)
        assertTrue(v.peakLeft.any { (it.toInt() and 0xFF) > 0 })
    }

    @Test
    fun clippedFixtureHasClipFlags() {
        val v = decodeVisuals(path("clip_lr_44k_stereo.wav"))
        val clipped = (0 until v.frameCount).count { v.isClipped(it) }
        // L clips at 2-4 seconds and 6-7 seconds (about 129 frames)
        assertTrue("clipped=$clipped", clipped in 100..160)
        assertTrue(!v.isClipped(0))
    }

    @Test
    fun undecodableInputsAreDecodeFailures() {
        assertDecodeFails(File(TestFixtures.workDir(), "does-not-exist.mp3").absolutePath)
        val zeroBytes = File(TestFixtures.workDir(), "zero_bytes.mp3").apply { writeBytes(ByteArray(0)) }
        assertDecodeFails(zeroBytes.absolutePath)
        assertDecodeFails(path("not_audio.mp3"))
        assertDecodeFails(path("empty_stereo.wav"))
    }

    @Test
    fun longerThan30MinutesIsRejected() {
        assertDecodeFails(path("silence_31min.opus"))
    }

    @Test
    fun requestedTokenCancelsDecode() {
        CancelToken().use { token ->
            token.request()
            assertThrows(CancellationException::class.java) {
                SegueCore.decode(path("click120_aminor_44k_stereo.wav"), token)
            }
        }
    }

    @Test
    fun requestFromAnotherThreadDuringDecodeIsSafe() {
        repeat(5) { i ->
            CancelToken().use { token ->
                val canceller = thread { Thread.sleep(i.toLong() * 3); token.request() }
                val result = runCatching {
                    SegueCore.decode(path("click120_aminor_44k_stereo.mp3"), token).close()
                }
                canceller.join()
                val e = result.exceptionOrNull()
                assertTrue("unexpected: $e", e == null || e is CancellationException)
            }
        }
    }

    @Test
    fun closedAudioIsRejectedOnKotlinSide() {
        val audio = CancelToken().use { token -> SegueCore.decode(path("tiny_2000_mono.wav"), token) }
        audio.close()
        audio.close()
        assertThrows(IllegalStateException::class.java) {
            SegueCore.writeVisuals(audio, File(TestFixtures.workDir(), "closed.sgv").absolutePath)
        }
    }
}
