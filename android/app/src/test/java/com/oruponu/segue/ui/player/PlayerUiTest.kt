package com.oruponu.segue.ui.player

import com.oruponu.segue.analysis.TrackAnalysisState
import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.StyleScore
import com.oruponu.segue.core.Visuals
import com.oruponu.segue.playback.PlayerUiState
import com.oruponu.segue.ui.analysis.StyleLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiTest {
    private val labels = StyleLabels((0 until 5).map { "Genre---Style $it" })

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
            waveformCount = 0,
            waveform = ByteArray(0),
        )

    private fun music(styles: List<StyleScore>) =
        MusicResult(
            bpm = 127.6f,
            bpmConfidence = 0.9f,
            keyNote = 9,
            keyScale = 1,
            keyConfidence = 0.8f,
            styles = styles,
        )

    @Test
    fun pendingAndVisualsReadyAreAnalyzing() {
        assertEquals(AnalysisUi.Analyzing, analysisUi(TrackAnalysisState.Pending, labels))
        assertEquals(
            AnalysisUi.Analyzing,
            analysisUi(TrackAnalysisState.VisualsReady(visuals), labels),
        )
    }

    @Test
    fun completeMapsBpmKeyAndStyles() {
        val styles = (0 until 5).map { StyleScore(it, 0.5f - it * 0.1f) }
        val ui = analysisUi(TrackAnalysisState.Complete(visuals, music(styles)), labels)
        assertEquals(
            AnalysisUi.Result(
                bpm = 128,
                key = "A Minor",
                styles = (0 until 5).map { StyleUi("Style $it", 0.5f - it * 0.1f) },
            ),
            ui,
        )
        assertEquals(listOf("BPM 128", "A Minor", "Style 0", "Style 1", "Style 2"), ui.chips())
    }

    @Test
    fun missingModelShowsBpmAndKeyOnly() {
        val ui = analysisUi(TrackAnalysisState.Complete(visuals, music(emptyList())), labels)
        assertEquals(listOf("BPM 128", "A Minor"), ui.chips())
    }

    @Test
    fun failedHidesChips() {
        val ui = analysisUi(TrackAnalysisState.Failed(SegueStatus.DECODE_FAILED, null), labels)
        assertEquals(AnalysisUi.Failed, ui)
        assertTrue(ui.chips().isEmpty())
    }

    @Test
    fun trackMissingFromLibraryKeepsItemMetadata() {
        val ui =
            PlayerUi(
                playback = PlayerUiState(currentKey = "k", title = "T", artist = "A"),
                track = null,
                analysis =
                    analysisUi(TrackAnalysisState.Failed(SegueStatus.IO_FAILED, null), labels),
            )
        assertEquals("T", ui.title)
        assertEquals("A", ui.artist)
        assertEquals(AnalysisUi.Failed, ui.analysis)
    }
}
