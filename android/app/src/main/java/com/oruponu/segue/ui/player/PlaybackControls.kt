package com.oruponu.segue.ui.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.oruponu.segue.R
import com.oruponu.segue.playback.PlayerUiState

@Immutable
class PlaybackCallbacks(
    val onPlayPause: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onSpeed: (Float) -> Unit,
    val onPitch: (Int) -> Unit,
)

@Composable
fun PlaybackControls(
    state: PlayerUiState,
    callbacks: PlaybackCallbacks,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(
                icon = R.drawable.ic_shuffle,
                description = R.string.shuffle,
                onClick = callbacks.onShuffle,
                tint = if (state.shuffle) primary else onSurface,
            )
            ControlButton(
                icon = R.drawable.ic_skip_previous,
                description = R.string.previous,
                onClick = callbacks.onPrevious,
                size = 48.dp,
            )
            val (icon, description) =
                when {
                    state.isEnded -> R.drawable.ic_replay to R.string.replay
                    state.isPlaying -> R.drawable.ic_pause to R.string.pause
                    else -> R.drawable.ic_play_arrow to R.string.play
                }
            FilledIconButton(onClick = callbacks.onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    painterResource(icon),
                    contentDescription = stringResource(description),
                    modifier = Modifier.size(32.dp),
                )
            }
            ControlButton(
                icon = R.drawable.ic_skip_next,
                description = R.string.next,
                onClick = callbacks.onNext,
                size = 48.dp,
                enabled = state.hasNext,
            )
            ControlButton(
                icon =
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one
                    else R.drawable.ic_repeat,
                description = R.string.repeat,
                onClick = callbacks.onRepeat,
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) onSurface else primary,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            PresetButton(
                label = speedLabel(state.speed),
                value = state.speed,
                default = 1f,
                presets = SPEED_PRESETS,
                presetLabel = ::speedLabel,
                onSelect = callbacks.onSpeed,
            )
            PresetButton(
                label = stringResource(R.string.pitch_label, semitoneLabel(state.pitchSemitones)),
                value = state.pitchSemitones,
                default = 0,
                presets = SEMITONE_PRESETS.toList(),
                presetLabel = ::semitoneLabel,
                onSelect = callbacks.onPitch,
            )
        }
    }
}

@Composable
private fun ControlButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(description),
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
        )
    }
}

/** Tap picks a preset from a menu; a long press resets a changed value to [default]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> PresetButton(
    label: String,
    value: T,
    default: T,
    presets: List<T>,
    presetLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier.width(72.dp)
            .height(40.dp)
            .combinedClickable(
                role = Role.Button,
                onClick = { expanded = true },
                onLongClick = {
                    if (value != default) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSelect(default)
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (value == default) MaterialTheme.colorScheme.onSurface else primary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            presets.forEach { preset ->
                val selected = preset == value
                DropdownMenuItem(
                    text = {
                        Text(
                            presetLabel(preset),
                            color = if (selected) primary else Color.Unspecified,
                            fontWeight = if (selected) FontWeight.Bold else null,
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(preset)
                    },
                )
            }
        }
    }
}
