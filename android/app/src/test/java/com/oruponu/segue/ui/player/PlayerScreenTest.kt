package com.oruponu.segue.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.oruponu.segue.R
import com.oruponu.segue.playback.PlayerUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var collapses = 0

    private val callbacks =
        PlaybackCallbacks(
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onShuffle = {},
            onRepeat = {},
            onSpeed = {},
            onPitch = {},
        )

    private val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    private fun setContent(analysis: AnalysisUi) {
        compose.setContent {
            PlayerScreen(
                ui =
                    PlayerUi(
                        playback = PlayerUiState(currentKey = "a", title = "T", artist = "A"),
                        analysis = analysis,
                    ),
                positionMs = 0,
                callbacks = callbacks,
                onCollapse = { collapses++ },
            )
        }
    }

    @Test
    fun analyzingShowsSpinner() {
        setContent(AnalysisUi.Analyzing)
        compose.onNode(spinner).assertExists()
    }

    @Test
    fun resultShowsChips() {
        setContent(
            AnalysisUi.Result(bpm = 128, key = "A Minor", styles = listOf(StyleUi("House", 0.5f))),
        )
        compose.onNodeWithText("BPM 128").assertExists()
        compose.onNodeWithText("A Minor").assertExists()
        compose.onNodeWithText("House").assertExists()
        compose.onNode(spinner).assertDoesNotExist()
    }

    @Test
    fun failedShowsNothing() {
        setContent(AnalysisUi.Failed)
        compose.onNode(spinner).assertDoesNotExist()
        compose.onNodeWithText("BPM", substring = true).assertDoesNotExist()
    }

    @Test
    fun collapseButtonCallsBack() {
        setContent(AnalysisUi.Failed)
        compose.onNodeWithText("T").assertExists()
        compose
            .onNodeWithContentDescription(compose.activity.getString(R.string.collapse_player))
            .performClick()
        assertEquals(1, collapses)
    }
}
