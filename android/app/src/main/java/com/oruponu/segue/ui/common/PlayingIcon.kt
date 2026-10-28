package com.oruponu.segue.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val BAR_COUNT = 3
private val BarWidth = 3.dp
private val BarGap = 2.dp
private val MaxHeight = 12.dp

@Composable
fun PlayingIcon(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "playing")
    val t =
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
            label = "phase",
        )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(BarWidth * BAR_COUNT + BarGap * (BAR_COUNT - 1), MaxHeight)) {
        val barWidth = BarWidth.toPx()
        val step = barWidth + BarGap.toPx()
        val radius = CornerRadius(1.dp.toPx())
        repeat(BAR_COUNT) { i ->
            val height = size.height * (0.5f + 0.5f * abs(sin(2 * PI.toFloat() * t.value + i)))
            drawRoundRect(
                color = color,
                topLeft = Offset(i * step, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius,
            )
        }
    }
}
