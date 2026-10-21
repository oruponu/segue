package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.concurrent.thread
import kotlin.coroutines.cancellation.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyzeTest {
    private fun decode(name: String): NativeAudio =
        CancelToken().use { token -> SegueCore.decode(TestFixtures.file(name).absolutePath, token) }

    @Test
    fun withoutModelsSkipsStyles() {
        decode("click120_aminor_44k_stereo.wav").use { audio ->
            val result = CancelToken().use { token -> SegueCore.analyze(audio, null, token) }
            assertTrue(result.styles.isEmpty())
            assertTrue("bpm=${result.bpm}", result.bpm > 0f)
            assertTrue(result.keyNote in 0..11)
        }
    }

    @Test
    fun withModelsReturnsFiveStylesInDescendingOrder() {
        SegueCore.loadModels(TestFixtures.model().absolutePath).use { models ->
            decode("click120_aminor_44k_stereo.wav").use { audio ->
                val result = CancelToken().use { token -> SegueCore.analyze(audio, models, token) }
                assertEquals(5, result.styles.size)
                assertTrue(result.styles.all { it.labelIndex in 0 until 400 })
                assertEquals(result.styles.sortedByDescending { it.confidence }, result.styles)
            }
        }
    }

    @Test
    fun missingModelFileIsModelLoadFailure() {
        val e =
            assertThrows(SegueException::class.java) {
                SegueCore.loadModels(File(TestFixtures.workDir(), "missing.onnx").absolutePath)
            }
        assertEquals(SegueStatus.MODEL_LOAD_FAILED, e.status)
    }

    @Test
    fun requestedTokenCancelsAnalyze() {
        decode("click120_aminor_44k_stereo.wav").use { audio ->
            CancelToken().use { token ->
                token.request()
                assertThrows(CancellationException::class.java) {
                    SegueCore.analyze(audio, null, token)
                }
            }
        }
    }

    @Test
    fun requestFromAnotherThreadDuringAnalyzeIsSafe() {
        SegueCore.loadModels(TestFixtures.model().absolutePath).use { models ->
            decode("click120_aminor_44k_stereo.wav").use { audio ->
                repeat(3) { i ->
                    CancelToken().use { token ->
                        val canceller = thread {
                            Thread.sleep(50L + i * 200L)
                            token.request()
                        }
                        val result = runCatching { SegueCore.analyze(audio, models, token) }
                        canceller.join()
                        val e = result.exceptionOrNull()
                        assertTrue("unexpected: $e", e == null || e is CancellationException)
                    }
                }
            }
        }
    }

    @Test
    fun closedModelsAreRejected() {
        val models = SegueCore.loadModels(TestFixtures.model().absolutePath)
        models.close()
        models.close()
        decode("tiny_2000_mono.wav").use { audio ->
            assertThrows(IllegalStateException::class.java) {
                CancelToken().use { token -> SegueCore.analyze(audio, models, token) }
            }
        }
    }

    @Test
    fun tooShortForStylePatchHasNoStylesOrFailsCleanly() {
        SegueCore.loadModels(TestFixtures.model().absolutePath).use { models ->
            decode("tiny_2000_mono.wav").use { audio ->
                val result = runCatching {
                    CancelToken().use { token -> SegueCore.analyze(audio, models, token) }
                }
                val e = result.exceptionOrNull()
                if (e == null) {
                    assertTrue(result.getOrThrow().styles.isEmpty())
                } else {
                    assertTrue(
                        "unexpected: $e",
                        e is SegueException && e.status == SegueStatus.ANALYSIS_FAILED,
                    )
                }
            }
        }
    }
}
