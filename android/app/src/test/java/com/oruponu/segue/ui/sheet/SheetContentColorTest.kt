package com.oruponu.segue.ui.sheet

import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SheetContentColorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun sheetContentUsesOnSurface() {
        val colors = darkColorScheme()
        val seen = mutableMapOf<String, Color>()
        compose.setContent {
            MaterialTheme(colorScheme = colors) {
                MainLayout(
                    showPlayer = true,
                    playerSheet = rememberSheetState(SheetValue.Expanded),
                    analysisSheet = rememberSheetState(),
                    content = {},
                    miniPlayer = { seen["miniPlayer"] = LocalContentColor.current },
                    player = { seen["player"] = LocalContentColor.current },
                    analysisButton = { seen["analysisButton"] = LocalContentColor.current },
                    analysis = { seen["analysis"] = LocalContentColor.current },
                )
            }
        }
        compose.runOnIdle {
            assertEquals(setOf("player", "analysisButton", "analysis"), seen.keys - "miniPlayer")
            seen.forEach { (slot, color) -> assertEquals(slot, colors.onSurface, color) }
        }
    }
}
