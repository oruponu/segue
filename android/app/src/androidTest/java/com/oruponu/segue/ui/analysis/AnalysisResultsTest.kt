package com.oruponu.segue.ui.analysis

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oruponu.segue.ui.player.AnalysisUi
import com.oruponu.segue.ui.player.StyleUi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisResultsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun failedShowsCannotAnalyze() {
        compose.setContent { AnalysisResults(AnalysisUi.Failed) }

        compose.onNodeWithText("解析できません").assertIsDisplayed()
    }

    @Test
    fun resultShowsBpmKeyAndStyles() {
        val styles = listOf(StyleUi("Techno", 0.42f), StyleUi("House", 0.3f))
        compose.setContent { AnalysisResults(AnalysisUi.Result(128, "A Minor", styles)) }

        compose.onNodeWithText("128").assertIsDisplayed()
        compose.onNodeWithText("A Minor").assertIsDisplayed()
        compose.onNodeWithText("Techno").assertIsDisplayed()
        compose.onNodeWithText("42.0%").assertIsDisplayed()
        compose.onNodeWithText("House").assertIsDisplayed()
    }

    @Test
    fun emptyResultShowsNoData() {
        compose.setContent { AnalysisResults(AnalysisUi.Result(null, null, emptyList())) }

        compose.onNodeWithText("解析データなし").assertIsDisplayed()
    }
}
