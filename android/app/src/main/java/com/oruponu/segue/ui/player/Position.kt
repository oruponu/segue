package com.oruponu.segue.ui.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import com.oruponu.segue.playback.PlayerUiState

fun extrapolatedPositionMs(state: PlayerUiState, nowElapsedMs: Long): Long {
    val position =
        if (state.isPlaying) {
            state.positionMs + ((nowElapsedMs - state.positionAtElapsedMs) * state.speed).toLong()
        } else {
            state.positionMs
        }
    val end = if (state.durationMs > 0) state.durationMs else Long.MAX_VALUE
    return position.coerceIn(0, end)
}

/** Advances every frame while playing; the state only carries the position at its last update. */
@Composable
fun rememberPositionMs(state: PlayerUiState): State<Long> {
    val position = remember {
        mutableLongStateOf(extrapolatedPositionMs(state, SystemClock.elapsedRealtime()))
    }
    LaunchedEffect(state) {
        position.longValue = extrapolatedPositionMs(state, SystemClock.elapsedRealtime())
        if (!state.isPlaying) return@LaunchedEffect
        while (true) {
            withFrameMillis {
                position.longValue = extrapolatedPositionMs(state, SystemClock.elapsedRealtime())
            }
        }
    }
    return position
}
