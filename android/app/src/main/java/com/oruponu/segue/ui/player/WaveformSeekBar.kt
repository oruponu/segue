package com.oruponu.segue.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oruponu.segue.core.Visuals
import com.oruponu.segue.ui.common.formatDuration
import kotlin.math.floor

private val BarHeight = 60.dp
private val BarWidth = 2.dp
private val BarStep = 3.dp
private val MinBarHeight = 2.dp

/** Bar i takes the waveform point at the same relative position, as an amplitude in 0..1. */
fun waveformBars(visuals: Visuals, barCount: Int): FloatArray {
    val count = visuals.waveformCount
    if (count == 0) return FloatArray(barCount)
    return FloatArray(barCount) { i ->
        val point = floor(i.toDouble() / barCount * count).toInt()
        (visuals.waveformMax(point) - visuals.waveformMin(point)) / 254f
    }
}

fun seekFraction(x: Float, width: Float): Float = (x / width).coerceIn(0f, 1f)

fun positionFraction(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

@Composable
fun WaveformSeekBar(
    visuals: Visuals?,
    positionMs: () -> Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Where the finger is while dragging; null shows the playback position.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val currentDuration by rememberUpdatedState(durationMs)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentPosition by rememberUpdatedState(positionMs)
    // Changes once a second, so the time text recomposes then rather than every frame.
    val shownTime by remember {
        derivedStateOf {
            val shownMs = dragFraction?.let { (currentDuration * it).toLong() } ?: currentPosition()
            formatDuration(shownMs)
        }
    }

    val color = MaterialTheme.colorScheme.onSurface
    Column(modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val barCount = floor(maxWidth / BarStep).toInt()
            val bars = remember(visuals, barCount) { visuals?.let { waveformBars(it, barCount) } }
            Canvas(
                Modifier.fillMaxWidth()
                    .height(BarHeight)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (currentDuration <= 0) return@detectTapGestures
                            val tapped = seekFraction(offset.x, size.width.toFloat())
                            currentOnSeek((currentDuration * tapped).toLong())
                        }
                    }
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                if (currentDuration > 0) {
                                    dragFraction = seekFraction(offset.x, size.width.toFloat())
                                }
                            },
                            onDragEnd = {
                                dragFraction?.let { currentOnSeek((currentDuration * it).toLong()) }
                                dragFraction = null
                            },
                            onDragCancel = { dragFraction = null },
                            onHorizontalDrag = { change, _ ->
                                if (dragFraction != null) {
                                    change.consume()
                                    dragFraction =
                                        seekFraction(change.position.x, size.width.toFloat())
                                }
                            },
                        )
                    },
            ) {
                val fraction = dragFraction ?: positionFraction(positionMs(), durationMs)
                val played = fraction * size.width
                if (bars == null) {
                    val height = MinBarHeight.toPx()
                    val top = (size.height - height) / 2
                    drawRect(color.copy(alpha = 0.24f), Offset(0f, top), Size(size.width, height))
                    drawRect(color.copy(alpha = 0.8f), Offset(0f, top), Size(played, height))
                } else {
                    val barWidth = BarWidth.toPx()
                    val step = BarStep.toPx()
                    val radius = CornerRadius(MinBarHeight.toPx())
                    bars.forEachIndexed { i, amplitude ->
                        val height =
                            (size.height * amplitude).coerceIn(MinBarHeight.toPx(), size.height)
                        val x = i * step
                        drawRoundRect(
                            color = if (x < played) color else color.copy(alpha = 0.24f),
                            topLeft = Offset(x, (size.height - height) / 2),
                            size = Size(barWidth, height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeText(shownTime)
            TimeText(formatDuration(durationMs.coerceAtLeast(0)))
        }
    }
}

@Composable
private fun TimeText(text: String) {
    Text(text = text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
