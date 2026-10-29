package com.oruponu.segue.ui.player

import com.oruponu.segue.analysis.TrackAnalysisState
import com.oruponu.segue.core.Visuals
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.playback.PlayerUiState
import com.oruponu.segue.ui.analysis.StyleLabels
import com.oruponu.segue.ui.analysis.keyName
import kotlin.math.roundToInt

private const val STYLE_CHIPS = 3

data class StyleUi(val name: String, val confidence: Float)

sealed interface AnalysisUi {
    data object Analyzing : AnalysisUi

    data class Result(val bpm: Int?, val key: String?, val styles: List<StyleUi>) : AnalysisUi

    data object Failed : AnalysisUi
}

fun analysisUi(state: TrackAnalysisState, labels: StyleLabels): AnalysisUi =
    when (state) {
        TrackAnalysisState.Pending,
        is TrackAnalysisState.VisualsReady -> AnalysisUi.Analyzing
        is TrackAnalysisState.Complete -> {
            val music = state.music
            AnalysisUi.Result(
                bpm = music.bpm.roundToInt().takeIf { it > 0 },
                key = keyName(music.keyNote, music.keyScale),
                styles =
                    music.styles.map { StyleUi(labels.displayName(it.labelIndex), it.confidence) },
            )
        }
        is TrackAnalysisState.Failed -> AnalysisUi.Failed
    }

fun AnalysisUi.chips(): List<String> {
    if (this !is AnalysisUi.Result) return emptyList()
    return listOfNotNull(bpm?.let { "BPM $it" }, key) + styles.take(STYLE_CHIPS).map { it.name }
}

fun TrackAnalysisState.visualsOrNull(): Visuals? =
    when (this) {
        TrackAnalysisState.Pending -> null
        is TrackAnalysisState.VisualsReady -> visuals
        is TrackAnalysisState.Complete -> visuals
        is TrackAnalysisState.Failed -> visuals
    }

/**
 * Title and artist come from the media item, so a track missing from the library still shows them.
 */
data class PlayerUi(
    val playback: PlayerUiState = PlayerUiState(),
    val track: TrackEntity? = null,
    val analysis: AnalysisUi = AnalysisUi.Analyzing,
    val visuals: Visuals? = null,
) {
    val title: String
        get() = playback.title.orEmpty()

    val artist: String?
        get() = playback.artist
}
