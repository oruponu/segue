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
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.max

private val MeterHeight = 44.dp
private val LeftLabelWidth = 36.dp
private val RightPadding = 4.dp
private val BarHeight = 10.dp
private val BarGap = 8.dp
private val BarRadius = 2.dp
private val ClipWidth = 4.dp
private val ClipGap = 4.dp
private val ValueMinWidth = 16.dp
private val ValueGap = 4.dp
private val HoldWidth = 2.dp
private val LabelGap = 4.dp
private val ScaleGap = 2.dp
private val ScaleTicks = listOf(-50, -30, -20, -10, -5, 0)

fun holdLabel(db: Float): String =
    if (db <= FLOOR_DB) "-inf" else String.format(Locale.ROOT, "%.1f", db)

/** [tick] is read while drawing, so the caller can redraw after updating [state]. */
@Composable
fun PeakMeter(state: MeterState, modifier: Modifier = Modifier, tick: () -> Long = { 0L }) {
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val channelStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 9.sp)
    val scaleStyle = TextStyle(color = colors.onSurfaceVariant, fontSize = 10.sp)
    val valueStyle = TextStyle(fontSize = 9.sp, fontFeatureSettings = "tnum")
    val channelLabels =
        remember(measurer, channelStyle) {
            listOf("L", "R").map { measurer.measure(it, channelStyle) }
        }
    val scaleLabels =
        remember(measurer, scaleStyle) { ScaleTicks.map { measurer.measure("$it", scaleStyle) } }
    val dbLabel = remember(measurer, scaleStyle) { measurer.measure("dB", scaleStyle) }
    // Digits are tabular, so this is the widest value; 16dp alone does not fit it at 9sp.
    val widestValue =
        remember(measurer, valueStyle) { measurer.measure("-00.0", valueStyle).size.width }
    val levelBrushColors =
        arrayOf(0f to colors.primary, 0.6f to colors.tertiary, 1f to colors.error)

    Canvas(modifier.fillMaxWidth().height(MeterHeight)) {
        tick()
        val valueWidth = max(ValueMinWidth.toPx(), widestValue.toFloat())
        val barLeft = LeftLabelWidth.toPx()
        val barRight =
            size.width -
                RightPadding.toPx() -
                valueWidth -
                ValueGap.toPx() -
                ClipWidth.toPx() -
                ClipGap.toPx()
        if (barRight <= barLeft) return@Canvas
        val barHeight = BarHeight.toPx()
        val bars =
            listOf(
                Rect(barLeft, 0f, barRight, barHeight),
                Rect(barLeft, barHeight + BarGap.toPx(), barRight, 2 * barHeight + BarGap.toPx()),
            )
        val radius = CornerRadius(BarRadius.toPx())
        val brush = Brush.horizontalGradient(*levelBrushColors, startX = barLeft, endX = barRight)
        val levels = listOf(state.peakLeftDb, state.peakRightDb)
        val holds = listOf(state.holdLeftDb, state.holdRightDb)

        bars.forEachIndexed { i, bar ->
            drawRoundRect(colors.outlineVariant, bar.topLeft, bar.size, radius)
            val level = peakMeterPosition(levels[i])
            if (level > 0f) {
                val filled = Rect(bar.left, bar.top, bar.left + level * bar.width, bar.bottom)
                clipPath(Path().apply { addRoundRect(RoundRect(filled, radius)) }) {
                    drawRect(brush, bar.topLeft, bar.size)
                }
            }
            val hold = peakMeterPosition(holds[i])
            if (hold > 0f) {
                val x = bar.left + hold * bar.width
                drawLine(
                    colors.onSurface,
                    Offset(x, bar.top),
                    Offset(x, bar.bottom),
                    HoldWidth.toPx(),
                )
            }
            drawCentred(
                channelLabels[i],
                barLeft - LabelGap.toPx() - channelLabels[i].size.width,
                bar,
            )
        }

        // One indicator for both channels, as the cache keeps one clip bit for L and R.
        drawRoundRect(
            if (state.clipping) colors.error else colors.outlineVariant,
            Offset(barRight + ClipGap.toPx(), 0f),
            Size(ClipWidth.toPx(), bars[1].bottom),
            radius,
        )

        val valueLeft = barRight + ClipGap.toPx() + ClipWidth.toPx() + ValueGap.toPx()
        val valueColor = if (state.clipping) colors.error else colors.onSurfaceVariant
        bars.forEachIndexed { i, bar ->
            val value = measurer.measure(holdLabel(holds[i]), valueStyle.copy(color = valueColor))
            drawCentred(value, valueLeft + valueWidth - value.size.width, bar)
        }

        val scaleY = bars[1].bottom + ScaleGap.toPx()
        ScaleTicks.forEachIndexed { i, db ->
            val label = scaleLabels[i]
            val x = barLeft + peakMeterPosition(db.toFloat()) * (barRight - barLeft)
            val left =
                when (i) {
                    0 -> barLeft
                    ScaleTicks.lastIndex -> x - label.size.width
                    else -> x - label.size.width / 2f
                }
            drawText(label, topLeft = Offset(left, scaleY))
        }
        drawText(dbLabel, topLeft = Offset(0f, scaleY))
    }
}

private fun DrawScope.drawCentred(text: TextLayoutResult, left: Float, row: Rect) {
    drawText(text, topLeft = Offset(left, row.top + (row.height - text.size.height) / 2f))
}
