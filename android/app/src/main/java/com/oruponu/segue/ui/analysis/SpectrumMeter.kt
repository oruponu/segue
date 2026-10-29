package com.oruponu.segue.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.log10

private val MeterHeight = 220.dp
private val LeftLabelWidth = 36.dp
private val BottomLabelHeight = 20.dp
private val BarGap = 3.dp
private val BarRadius = 2.dp
private val GridWidth = 0.5.dp
private val LabelGap = 4.dp

private const val MIN_HZ = 20f
private const val MAX_HZ = 20_000f
private val LevelTicks = listOf(0, -10, -20, -30, -40, -50)
private val FrequencyTicks =
    listOf(20f to "20", 100f to "100", 1_000f to "1k", 5_000f to "5k", 20_000f to "20k")

/** Position on the 20Hz to 20kHz log axis, in 0..1. */
fun frequencyFraction(hz: Float): Float {
    val clamped = hz.coerceIn(MIN_HZ, MAX_HZ)
    return (log10(clamped) - log10(MIN_HZ)) / (log10(MAX_HZ) - log10(MIN_HZ))
}

fun levelFraction(db: Float): Float = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)

/**
 * [tick] is read while drawing, so the caller can redraw the bars after changing [spectrumDb] in
 * place.
 */
@Composable
fun SpectrumMeter(
    spectrumDb: FloatArray?,
    modifier: Modifier = Modifier,
    tick: () -> Long = { 0L },
) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val unitStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp)
    val levelLabels =
        remember(measurer, labelStyle) { LevelTicks.map { measurer.measure("$it", labelStyle) } }
    val frequencyLabels =
        remember(measurer, labelStyle) {
            FrequencyTicks.map { (_, label) -> measurer.measure(label, labelStyle) }
        }
    val dbLabel = remember(measurer, unitStyle) { measurer.measure("dB", unitStyle) }
    val hzLabel = remember(measurer, unitStyle) { measurer.measure("Hz", unitStyle) }

    Canvas(modifier.fillMaxWidth().height(MeterHeight)) {
        tick()
        val area =
            Rect(LeftLabelWidth.toPx(), 0f, size.width, size.height - BottomLabelHeight.toPx())
        val labelGap = LabelGap.toPx()

        LevelTicks.forEachIndexed { i, db ->
            val y = area.bottom - levelFraction(db.toFloat()) * area.height
            drawLine(
                colors.outlineVariant,
                Offset(area.left, y),
                Offset(area.right, y),
                strokeWidth = GridWidth.toPx(),
            )
            val label = levelLabels[i]
            drawText(
                label,
                topLeft =
                    Offset(area.left - label.size.width - labelGap, y - label.size.height / 2f),
            )
        }
        drawText(dbLabel, topLeft = Offset(0f, area.top - 2.dp.toPx()))

        val labelY = area.bottom + labelGap
        FrequencyTicks.forEachIndexed { i, (hz, _) ->
            val label = frequencyLabels[i]
            val x =
                when (i) {
                    // The end labels sit inside the axis instead of centring on their ticks.
                    0 -> area.left
                    FrequencyTicks.lastIndex -> area.right - label.size.width
                    else -> area.left + frequencyFraction(hz) * area.width - label.size.width / 2f
                }
            drawText(label, topLeft = Offset(x, labelY))
        }
        drawText(hzLabel, topLeft = Offset(0f, labelY))

        val bands = spectrumDb ?: return@Canvas
        if (bands.isEmpty()) return@Canvas
        val gap = BarGap.toPx()
        val barWidth = (area.width - gap * (bands.size - 1)) / bands.size
        if (barWidth <= 0f) return@Canvas
        val radius = CornerRadius(BarRadius.toPx())
        bands.forEachIndexed { i, db ->
            val fraction = levelFraction(db)
            val height = fraction * area.height
            if (height < 1f) return@forEachIndexed
            drawRoundRect(
                color = lerp(colors.primaryContainer, colors.primary, fraction).copy(alpha = 0.75f),
                topLeft = Offset(area.left + i * (barWidth + gap), area.bottom - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}
