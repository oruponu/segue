package com.oruponu.segue.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oruponu.segue.analysis.AnalysisRepository
import com.oruponu.segue.analysis.TrackAnalysisState
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.LibraryRepository
import com.oruponu.segue.playback.PlaybackConnection
import com.oruponu.segue.playback.PlaybackEvent
import com.oruponu.segue.playback.PlayerUiState
import com.oruponu.segue.ui.analysis.StyleLabels
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private class TrackDetails(
    val key: String,
    val track: TrackEntity?,
    val analysis: TrackAnalysisState,
)

/**
 * Joins the playback state with the current track's row and analysis. Until the new track's details
 * arrive, the previous track's are dropped rather than shown under the new title.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun playerUi(
    playback: Flow<PlayerUiState>,
    track: (String) -> Flow<TrackEntity?>,
    analysis: (String) -> Flow<TrackAnalysisState>,
    labels: () -> StyleLabels,
): Flow<PlayerUi> {
    val details =
        playback
            .map { it.currentKey }
            .distinctUntilChanged()
            .flatMapLatest { key ->
                if (key == null) {
                    flowOf(null)
                } else {
                    combine(track(key), analysis(key)) { row, state ->
                        TrackDetails(key, row, state)
                    }
                }
            }
    return combine(playback, details) { state, details ->
        val current = details?.takeIf { it.key == state.currentKey }
        if (current == null) {
            PlayerUi(playback = state)
        } else {
            PlayerUi(
                playback = state,
                track = current.track,
                analysis = analysisUi(current.analysis, labels()),
                visuals = current.analysis.visualsOrNull(),
            )
        }
    }
}

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val connection: PlaybackConnection,
    library: LibraryRepository,
    analysis: AnalysisRepository,
    // Lazy, so the labels asset is read on the Default dispatcher below, not on main.
    labels: Lazy<StyleLabels>,
) : ViewModel() {
    val state: StateFlow<PlayerUi> =
        playerUi(connection.state, library::observeTrack, analysis::state, labels::get)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerUi())

    val events: Flow<PlaybackEvent> = connection.events

    fun play(tracks: List<TrackEntity>, index: Int) = connection.playAlbum(tracks, index)

    fun togglePlayPause() = connection.togglePlayPause()

    fun next() = connection.next()

    fun previous() = connection.previous()

    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)

    fun toggleShuffle() = connection.toggleShuffle()

    fun cycleRepeat() = connection.cycleRepeat()

    fun setSpeed(speed: Float) = connection.setSpeed(speed)

    fun setPitch(semitones: Int) = connection.setPitchSemitones(semitones)
}
