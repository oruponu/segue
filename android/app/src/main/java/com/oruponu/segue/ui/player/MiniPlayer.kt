package com.oruponu.segue.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oruponu.segue.R
import com.oruponu.segue.ui.common.TrackArtwork
import com.oruponu.segue.ui.library.artistName

// The row and the progress bar together fill the sheet's 72dp peek.
private val RowHeight = 70.dp
private val ProgressHeight = 2.dp

/**
 * [progressOnTop] = false puts the progress above the row and the navigation bar inset below it;
 * true puts the progress below the row, for the top edge of the analysis sheet.
 */
@Composable
fun MiniPlayer(
    ui: PlayerUi,
    positionMs: Long,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    progressOnTop: Boolean = false,
) {
    val playback = ui.playback
    val fraction =
        if (playback.durationMs > 0) (positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
        else 0f
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)),
    ) {
        if (!progressOnTop) Progress(fraction)
        Row(
            Modifier.fillMaxWidth().height(RowHeight).padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(
                ui.track,
                size = 48.dp,
                cornerRadius = 4.dp,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    artistName(ui.artist),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                val (icon, description) =
                    if (playback.isPlaying) R.drawable.ic_pause to R.string.pause
                    else R.drawable.ic_play_arrow to R.string.play
                Icon(painterResource(icon), contentDescription = stringResource(description))
            }
            IconButton(onClick = onNext, enabled = playback.hasNext) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    contentDescription = stringResource(R.string.next),
                )
            }
        }
        if (progressOnTop) {
            Progress(fraction)
        } else {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun Progress(fraction: Float) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(ProgressHeight),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
