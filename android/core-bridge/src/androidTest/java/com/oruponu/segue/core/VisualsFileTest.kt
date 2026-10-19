package com.oruponu.segue.core

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualsFileTest {
    private lateinit var written: File

    @Before
    fun writeWavVisuals() {
        written = File(TestFixtures.workDir(), "file-test.sgv")
        CancelToken().use { token ->
            SegueCore.decode(TestFixtures.file("click120_aminor_44k_stereo.wav").absolutePath, token).use { audio ->
                SegueCore.writeVisuals(audio, written.absolutePath)
            }
        }
    }

    private fun assertReadFails(file: File) {
        val e = assertThrows(SegueException::class.java) { SegueCore.readVisuals(file.absolutePath) }
        assertEquals(SegueStatus.IO_FAILED, e.status)
    }

    private fun variant(name: String, mutate: (ByteArray) -> ByteArray): File =
        File(TestFixtures.workDir(), name).apply { writeBytes(mutate(written.readBytes())) }

    @Test
    fun fileSizeMatchesFormat() {
        // Header 28 + spectrum 1292×32 + peaks 1292×2 + clips 162 + waveform 3000×2
        assertEquals(28L + 1292 * 32 + 1292 * 2 + 162 + 6000, written.length())
    }

    @Test
    fun missingFileIsIoFailure() {
        assertReadFails(File(TestFixtures.workDir(), "missing.sgv"))
    }

    @Test
    fun writeToMissingDirectoryIsIoFailure() {
        val e = assertThrows(SegueException::class.java) {
            CancelToken().use { token ->
                SegueCore.decode(TestFixtures.file("tiny_2000_mono.wav").absolutePath, token).use { audio ->
                    SegueCore.writeVisuals(audio, File(TestFixtures.workDir(), "no-such-dir/x.sgv").absolutePath)
                }
            }
        }
        assertEquals(SegueStatus.IO_FAILED, e.status)
    }

    @Test
    fun truncatedFileIsRejected() {
        assertReadFails(variant("truncated.sgv") { it.copyOf(it.size - 1) })
    }

    @Test
    fun wrongMagicIsRejected() {
        assertReadFails(variant("magic.sgv") { it.also { b -> b[0] = 'X'.code.toByte() } })
    }

    @Test
    fun otherAnalysisVersionIsRejected() {
        // analysis_version is the u16 at offset 6 (little-endian)
        assertReadFails(variant("version.sgv") { it.also { b -> b[6] = 2; b[7] = 0 } })
    }

    @Test
    fun hugeFrameCountIsRejectedBeforeAllocating() {
        // frame_count is the u32 at offset 16. 0xFFFFFFFF exceeds the 30-minute limit
        assertReadFails(variant("huge.sgv") { it.also { b -> for (i in 16..19) b[i] = 0xFF.toByte() } })
    }
}
