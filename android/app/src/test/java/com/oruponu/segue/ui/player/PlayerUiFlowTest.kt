package com.oruponu.segue.ui.player

import com.oruponu.segue.analysis.TrackAnalysisState
import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.Visuals
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.playback.PlayerUiState
import com.oruponu.segue.ui.analysis.StyleLabels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerUiFlowTest {
    private val labels = StyleLabels(listOf("Genre---Style"))

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

    private val complete =
        TrackAnalysisState.Complete(
            visuals,
            MusicResult(
                bpm = 120f,
                bpmConfidence = 0.9f,
                keyNote = 0,
                keyScale = 0,
                keyConfidence = 0.8f,
                styles = emptyList(),
            ),
        )

    private fun row(key: String) =
        TrackEntity(
            key = key,
            mediaStoreId = 1L,
            relativePath = "Music/",
            displayName = "$key.mp3",
            title = key,
            album = "Album",
            artist = null,
            albumArtist = null,
            discNumber = null,
            trackNumber = null,
            durationMs = 1000L,
            size = 10L,
            dateModified = 5L,
            albumKey = "ak",
        )

    private class Sources(
        val tracks: Map<String, Flow<TrackEntity?>>,
        val analyses: Map<String, Flow<TrackAnalysisState>>,
    ) {
        val trackCalls = mutableListOf<String>()

        fun track(key: String): Flow<TrackEntity?> {
            trackCalls += key
            return tracks[key] ?: emptyFlow()
        }

        fun analysis(key: String): Flow<TrackAnalysisState> = analyses[key] ?: emptyFlow()
    }

    @Test
    fun noCurrentTrackShowsPlaybackOnly() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = MutableStateFlow(PlayerUiState())
            val sources = Sources(emptyMap(), emptyMap())
            val values = mutableListOf<PlayerUi>()
            backgroundScope.launch {
                playerUi(playback, sources::track, sources::analysis) { labels }
                    .collect { values += it }
            }

            assertEquals(PlayerUi(), values.last())
            assertEquals(emptyList<String>(), sources.trackCalls)
        }

    @Test
    fun currentTrackJoinsRowAndAnalysis() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = MutableStateFlow(PlayerUiState(currentKey = "a", title = "T"))
            val sources =
                Sources(
                    mapOf("a" to MutableStateFlow(row("a"))),
                    mapOf("a" to MutableStateFlow(complete)),
                )
            val values = mutableListOf<PlayerUi>()
            backgroundScope.launch {
                playerUi(playback, sources::track, sources::analysis) { labels }
                    .collect { values += it }
            }

            val ui = values.last()
            assertEquals("T", ui.title)
            assertEquals(row("a"), ui.track)
            assertEquals(
                AnalysisUi.Result(bpm = 120, key = "C Major", styles = emptyList()),
                ui.analysis,
            )
            assertNotNull(ui.visuals)
        }

    @Test
    fun switchingTrackDropsPreviousDetails() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = MutableStateFlow(PlayerUiState(currentKey = "a"))
            val sources =
                Sources(
                    mapOf("a" to MutableStateFlow(row("a"))),
                    mapOf("a" to MutableStateFlow(complete)),
                )
            val values = mutableListOf<PlayerUi>()
            backgroundScope.launch {
                playerUi(playback, sources::track, sources::analysis) { labels }
                    .collect { values += it }
            }

            playback.value = PlayerUiState(currentKey = "b", title = "B")

            val ui = values.last()
            assertEquals("B", ui.title)
            assertNull(ui.track)
            assertEquals(AnalysisUi.Analyzing, ui.analysis)
            assertNull(ui.visuals)
        }

    @Test
    fun positionUpdatesKeepTheSubscription() =
        runTest(UnconfinedTestDispatcher()) {
            val playback = MutableStateFlow(PlayerUiState(currentKey = "a"))
            val sources =
                Sources(
                    mapOf("a" to MutableStateFlow(row("a"))),
                    mapOf("a" to MutableStateFlow(complete)),
                )
            val values = mutableListOf<PlayerUi>()
            backgroundScope.launch {
                playerUi(playback, sources::track, sources::analysis) { labels }
                    .collect { values += it }
            }

            playback.value = PlayerUiState(currentKey = "a", positionMs = 5000)

            assertEquals(5000L, values.last().playback.positionMs)
            assertEquals(row("a"), values.last().track)
            assertEquals(listOf("a"), sources.trackCalls)
        }
}
