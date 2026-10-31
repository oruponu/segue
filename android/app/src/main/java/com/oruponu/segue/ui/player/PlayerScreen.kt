package com.oruponu.segue.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oruponu.segue.R
import com.oruponu.segue.ui.common.TrackArtwork
import com.oruponu.segue.ui.library.artistName

// Space for the collapsed analysis sheet, which covers the bottom of this screen.
private val AnalysisButtonHeight = 56.dp

@Composable
fun PlayerScreen(
    ui: PlayerUi,
    positionMs: () -> Long,
    callbacks: PlaybackCallbacks,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        IconButton(onClick = onCollapse, modifier = Modifier.padding(top = 8.dp)) {
            Icon(
                painterResource(R.drawable.ic_keyboard_arrow_down),
                contentDescription = stringResource(R.string.collapse_player),
                modifier = Modifier.size(32.dp),
            )
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            TrackArtwork(
                ui.track,
                size = 250.dp,
                cornerRadius = 12.dp,
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(12.dp)),
            )
            Spacer(Modifier.height(30.dp))
            MarqueeText(ui.title) {
                Text(it, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            MarqueeText(artistName(ui.artist)) {
                Text(
                    it,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            AnalysisChips(ui.analysis)
            Spacer(Modifier.height(20.dp))
            WaveformSeekBar(
                visuals = ui.visuals,
                positionMs = positionMs,
                durationMs = ui.playback.durationMs,
                onSeek = callbacks.onSeek,
            )
            PlaybackControls(ui.playback, callbacks)
        }
        Spacer(Modifier.height(AnalysisButtonHeight))
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun MarqueeText(text: String, content: @Composable (String) -> Unit) {
    Box(
        Modifier.basicMarquee(
            initialDelayMillis = 2000,
            repeatDelayMillis = 1000,
            velocity = 50.dp,
        ),
    ) {
        content(text)
    }
}

@Composable
private fun AnalysisChips(analysis: AnalysisUi) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (analysis) {
            AnalysisUi.Analyzing ->
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            is AnalysisUi.Result -> {
                // chips() puts the styles after the BPM and the key.
                val firstStyle = listOfNotNull(analysis.bpm, analysis.key).size
                analysis.chips().forEachIndexed { i, label ->
                    AssistChip(
                        onClick = {},
                        label = { Text(label) },
                        colors =
                            if (i >= firstStyle) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                    )
                }
            }
            AnalysisUi.Failed -> {}
        }
    }
}
