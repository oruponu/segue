package com.oruponu.segue.ui.analysis

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.oruponu.segue.playback.PlayerUiState
import com.oruponu.segue.ui.player.AnalysisUi
import com.oruponu.segue.ui.player.PlaybackCallbacks
import com.oruponu.segue.ui.player.PlayerUi
import com.oruponu.segue.ui.sheet.SheetState
import com.oruponu.segue.ui.sheet.SheetValue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisSheetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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

    private val ui =
        PlayerUi(
            playback = PlayerUiState(currentKey = "a", title = "T", artist = "A"),
            analysis = AnalysisUi.Failed,
        )

    private val sheet = SheetState(SheetValue.Expanded)

    private fun setContent() {
        compose.setContent { AnalysisSheet(ui, positionMs = 0, sheet = sheet, callbacks) }
    }

    @Test
    fun realtimeTabIsSelectedFirst() {
        setContent()

        compose.onNodeWithText("リアルタイム").assertIsSelected()
        compose.onNodeWithText("解析結果").assertIsNotSelected()
        compose.onNodeWithText("解析できません").assertDoesNotExist()
    }

    @Test
    fun resultsTabShowsAnalysis() {
        setContent()

        compose.onNodeWithText("解析結果").performClick()

        compose.onNodeWithText("解析結果").assertIsSelected()
        compose.onNodeWithText("解析できません").assertExists()
    }

    @Test
    fun tappingMiniPlayerCollapsesSheet() {
        setContent()

        compose.onNodeWithText("T").performClick()
        compose.waitForIdle()

        assertFalse(sheet.isExpanded)
    }
}
