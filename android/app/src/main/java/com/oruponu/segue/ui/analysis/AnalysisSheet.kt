package com.oruponu.segue.ui.analysis

import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.ui.player.MiniPlayer
import com.oruponu.segue.ui.player.PlaybackCallbacks
import com.oruponu.segue.ui.player.PlayerUi
import com.oruponu.segue.ui.player.extrapolatedPositionMs
import com.oruponu.segue.ui.sheet.SheetState
import kotlinx.coroutines.launch

private enum class AnalysisTab(@param:StringRes val label: Int) {
    Realtime(R.string.realtime),
    Results(R.string.results),
}

@Composable
fun AnalysisSheet(
    ui: PlayerUi,
    positionMs: Long,
    sheet: SheetState,
    callbacks: PlaybackCallbacks,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(AnalysisTab.Realtime) }
    val bandCount = ui.visuals?.bandCount ?: 0
    val meter = remember(bandCount) { MeterState(bandCount) }
    val frame = remember { mutableLongStateOf(0L) }
    val currentUi by rememberUpdatedState(ui)
    val running = sheet.isExpanded && tab == AnalysisTab.Realtime
    val playing = ui.playback.isPlaying

    LaunchedEffect(running, playing, meter) {
        if (!running) return@LaunchedEffect
        var last = 0L
        // Once paused meters have fallen to the floor the loop ends, so the frame clock goes idle.
        while (currentUi.playback.isPlaying || meter.isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60 else (now - last) / 1e9f
                last = now
                val playback = currentUi.playback
                val visuals = currentUi.visuals?.takeIf { it.frameCount > 0 }
                val target =
                    if (playback.isPlaying && visuals != null) {
                        visuals.meterTargetAt(
                            extrapolatedPositionMs(playback, SystemClock.elapsedRealtime()),
                        )
                    } else {
                        null
                    }
                meter.update(target, dt)
                frame.longValue++
            }
        }
    }

    Column(modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        MiniPlayer(
            ui = ui,
            positionMs = positionMs,
            onPlayPause = callbacks.onPlayPause,
            onNext = callbacks.onNext,
            modifier = Modifier.clickable { scope.launch { sheet.collapse() } },
            progressOnTop = true,
        )
        SingleChoiceSegmentedButtonRow(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            AnalysisTab.entries.forEachIndexed { i, entry ->
                SegmentedButton(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    shape = SegmentedButtonDefaults.itemShape(i, AnalysisTab.entries.size),
                ) {
                    Text(stringResource(entry.label))
                }
            }
        }
        when (tab) {
            AnalysisTab.Realtime ->
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    PeakMeter(meter, tick = { frame.longValue })
                    Spacer(Modifier.height(64.dp))
                    SpectrumMeter(meter.spectrumDb, tick = { frame.longValue })
                }
            AnalysisTab.Results -> AnalysisResults(ui.analysis, Modifier.weight(1f))
        }
    }
}
