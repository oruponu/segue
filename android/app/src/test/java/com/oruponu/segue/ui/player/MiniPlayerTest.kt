package com.oruponu.segue.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertRangeInfoEquals
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
class MiniPlayerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var playPauses = 0

    private fun setContent(playback: PlayerUiState, positionMs: Long = 0) {
        compose.setContent {
            MiniPlayer(
                ui = PlayerUi(playback = playback),
                positionMs = { positionMs },
                onPlayPause = { playPauses++ },
                onNext = {},
            )
        }
    }

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun showsTitleAndUnknownArtist() {
        setContent(PlayerUiState(currentKey = "a", title = "T", artist = null))
        compose.onNodeWithText("T").assertExists()
        compose.onNodeWithText(string(R.string.unknown_artist)).assertExists()
    }

    @Test
    fun pauseCallsBackAndNextNeedsANextTrack() {
        setContent(PlayerUiState(currentKey = "a", isPlaying = true, hasNext = false))
        compose.onNodeWithContentDescription(string(R.string.pause)).performClick()
        assertEquals(1, playPauses)
        compose.onNodeWithContentDescription(string(R.string.next)).assertIsNotEnabled()
    }

    @Test
    fun progressFollowsPosition() {
        setContent(PlayerUiState(currentKey = "a", durationMs = 100_000), positionMs = 25_000)
        compose
            .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo(0.25f, 0f..1f)))
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.25f, 0f..1f))
    }
}
