package com.oruponu.segue.ui.sheet

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

enum class SheetValue {
    Collapsed,
    Expanded,
}

private val MoveSpec = tween<Float>(durationMillis = 200, easing = EaseOutCubic)

@Stable
class SheetState(initial: SheetValue) {
    internal val draggable = AnchoredDraggableState(initial)

    // A move requested before the sheet is laid out; the anchors take it when they are set.
    private var pending by mutableStateOf<SheetValue?>(null)
    private var collapsedOffset = Float.NaN

    private val hasAnchors: Boolean
        get() = draggable.anchors.size > 0

    private val value: SheetValue
        get() = pending ?: draggable.currentValue

    /** 0 when collapsed and 1 when expanded. */
    val progress: Float
        get() =
            if (!hasAnchors || draggable.offset.isNaN()) {
                if (value == SheetValue.Expanded) 1f else 0f
            } else {
                draggable.progress(SheetValue.Collapsed, SheetValue.Expanded)
            }

    val isExpanded: Boolean
        get() = (pending ?: draggable.targetValue) == SheetValue.Expanded

    suspend fun expand() = moveTo(SheetValue.Expanded)

    suspend fun collapse() = moveTo(SheetValue.Collapsed)

    private suspend fun moveTo(target: SheetValue) {
        if (hasAnchors) draggable.animateTo(target, MoveSpec) else pending = target
    }

    internal fun setAnchors(collapsed: Float) {
        if (collapsed == collapsedOffset && pending == null) return
        collapsedOffset = collapsed
        draggable.updateAnchors(
            DraggableAnchors {
                SheetValue.Collapsed at collapsed
                SheetValue.Expanded at 0f
            },
            pending ?: draggable.targetValue,
        )
        pending = null
    }

    internal fun placementOffset(): Float =
        draggable.offset.takeUnless { it.isNaN() }
            ?: if (value == SheetValue.Expanded) 0f else collapsedOffset

    companion object {
        val Saver: Saver<SheetState, SheetValue> =
            Saver(
                save = { if (it.isExpanded) SheetValue.Expanded else SheetValue.Collapsed },
                restore = { SheetState(it) },
            )
    }
}

@Composable
fun rememberSheetState(initial: SheetValue = SheetValue.Collapsed): SheetState =
    rememberSaveable(saver = SheetState.Saver) { SheetState(initial) }
