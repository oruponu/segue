package com.oruponu.segue.analysis

import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.Visuals

sealed interface TrackAnalysisState {
    data object Pending : TrackAnalysisState

    data class VisualsReady(val visuals: Visuals) : TrackAnalysisState

    data class Complete(val visuals: Visuals, val music: MusicResult) : TrackAnalysisState

    data class Failed(val status: SegueStatus, val visuals: Visuals?) : TrackAnalysisState
}
