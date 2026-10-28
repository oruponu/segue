package com.oruponu.segue.ui.sheet

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

object SheetTags {
    const val MINI_PLAYER = "miniPlayer"
    const val PLAYER = "player"
    const val ANALYSIS_BUTTON = "analysisButton"
    const val ANALYSIS = "analysis"
}

private val MiniPlayerHeight = 72.dp
private val AnalysisButtonHeight = 56.dp
private val SnapSpec = tween<Float>(durationMillis = 150)

@Composable
fun MainLayout(
    showPlayer: Boolean,
    playerSheet: SheetState,
    analysisSheet: SheetState,
    content: @Composable (PaddingValues) -> Unit,
    miniPlayer: @Composable () -> Unit,
    player: @Composable () -> Unit,
    analysisButton: @Composable () -> Unit,
    analysis: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(playerSheet, analysisSheet) {
        snapshotFlow { playerSheet.isExpanded }.filter { !it }.collect { analysisSheet.collapse() }
    }

    Box(modifier.fillMaxSize()) {
        content(PaddingValues(bottom = if (showPlayer) MiniPlayerHeight + navigationBar else 0.dp))
        if (showPlayer) {
            Sheet(
                state = playerSheet,
                peekHeight = MiniPlayerHeight + navigationBar,
                expandedTag = SheetTags.PLAYER,
                collapsedTag = SheetTags.MINI_PLAYER,
                expanded = {
                    player()
                    Sheet(
                        state = analysisSheet,
                        peekHeight = AnalysisButtonHeight + navigationBar,
                        expandedTag = SheetTags.ANALYSIS,
                        collapsedTag = SheetTags.ANALYSIS_BUTTON,
                        expanded = analysis,
                        collapsed = analysisButton,
                    )
                },
                collapsed = miniPlayer,
            )
            // Composed last, so the analysis handler takes the back event before the player's.
            SheetBackHandler(playerSheet)
            SheetBackHandler(analysisSheet)
        }
    }
}

/**
 * A sheet that fills its parent and, when collapsed, shows only [peekHeight] at the bottom. The two
 * layers fade with [SheetState.progress]; the expanded one takes input once progress reaches 0.5,
 * the collapsed one below that.
 */
@Composable
private fun Sheet(
    state: SheetState,
    peekHeight: Dp,
    expandedTag: String,
    collapsedTag: String,
    expanded: @Composable () -> Unit,
    collapsed: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val fling =
        AnchoredDraggableDefaults.flingBehavior(
            state.draggable,
            positionalThreshold = { it / 2 },
            animationSpec = SnapSpec,
        )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val collapsedOffset = constraints.maxHeight - with(density) { peekHeight.toPx() }
        SideEffect { state.setAnchors(collapsedOffset) }
        Box(
            Modifier.fillMaxSize()
                .offset { IntOffset(0, state.placementOffset().roundToInt()) }
                .anchoredDraggable(state.draggable, Orientation.Vertical, flingBehavior = fling)
                .background(MaterialTheme.colorScheme.surface),
        ) {
            val expandedActive = state.progress >= 0.5f
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { alpha = state.progress }
                    .then(
                        if (expandedActive) Modifier.testTag(expandedTag)
                        else Modifier.clearAndSetSemantics {},
                    ),
            ) {
                expanded()
            }
            if (!expandedActive) {
                // Keeps taps off the faded expanded layer; drags still reach the sheet.
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures {} })
                Box(
                    Modifier.fillMaxWidth()
                        .height(peekHeight)
                        .graphicsLayer { alpha = (1 - 2 * state.progress).coerceIn(0f, 1f) }
                        .testTag(collapsedTag)
                        .clickable { scope.launch { state.expand() } },
                ) {
                    collapsed()
                }
            }
        }
    }
}

/**
 * Collapses an expanded sheet on back, following a predictive back gesture. While the sheet is
 * collapsed the handler is off, so the event falls through to the navigation below.
 */
@Composable
private fun SheetBackHandler(state: SheetState) {
    val scope = rememberCoroutineScope()
    // A gesture past halfway flips isExpanded; staying enabled keeps the gesture alive.
    var inGesture by remember { mutableStateOf(false) }
    PredictiveBackHandler(enabled = inGesture || state.isExpanded) { events ->
        inGesture = true
        try {
            events.collect { state.seekTowardsCollapsed(it.progress) }
            state.collapse()
        } catch (e: CancellationException) {
            scope.launch { state.expand() }
            throw e
        } finally {
            inGesture = false
        }
    }
}
