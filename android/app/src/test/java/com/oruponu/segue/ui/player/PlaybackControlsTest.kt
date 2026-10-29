package com.oruponu.segue.ui.player

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
class PlaybackControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var playPauses = 0
    private val speeds = mutableListOf<Float>()
    private val pitches = mutableListOf<Int>()

    private val callbacks =
        PlaybackCallbacks(
            onPlayPause = { playPauses++ },
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onShuffle = {},
            onRepeat = {},
            onSpeed = { speeds += it },
            onPitch = { pitches += it },
        )

    private fun setContent(state: PlayerUiState) {
        compose.setContent { PlaybackControls(state, callbacks) }
    }

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun endedShowsReplay() {
        setContent(PlayerUiState(currentKey = "a", isEnded = true))
        compose.onNodeWithContentDescription(string(R.string.replay)).performClick()
        assertEquals(1, playPauses)
    }

    @Test
    fun nextIsDisabledWithoutNext() {
        setContent(PlayerUiState(currentKey = "a", hasNext = false))
        compose.onNodeWithContentDescription(string(R.string.next)).assertIsNotEnabled()
    }

    @Test
    fun speedMenuSelectsPreset() {
        setContent(PlayerUiState(currentKey = "a"))
        compose.onNodeWithText("1.0x").performClick()
        compose.onNodeWithText("0.75x").performClick()
        assertEquals(listOf(0.75f), speeds)
    }

    @Test
    fun longPressResetsOnlyChangedValues() {
        setContent(PlayerUiState(currentKey = "a", speed = 1.5f, pitchSemitones = 0))
        compose.onNodeWithText("1.5x").performTouchInput { longClick() }
        compose
            .onNodeWithText(compose.activity.getString(R.string.pitch_label, "0"))
            .performTouchInput {
                longClick()
            }
        assertEquals(listOf(1f), speeds)
        assertEquals(emptyList<Int>(), pitches)
    }
}
