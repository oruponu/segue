package com.oruponu.segue.ui.sheet

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.oruponu.segue.ui.AlbumDetail
import com.oruponu.segue.ui.Albums
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var player: SheetState
    private lateinit var analysis: SheetState
    private lateinit var scope: CoroutineScope
    private lateinit var backStack: NavBackStack<NavKey>
    private var showPlayer by mutableStateOf(true)
    private var contentPadding: PaddingValues? = null

    private fun setContent(show: Boolean = true) {
        showPlayer = show
        compose.setContent {
            player = rememberSheetState()
            analysis = rememberSheetState()
            scope = rememberCoroutineScope()
            backStack = rememberNavBackStack(Albums, AlbumDetail("a"))
            MainLayout(
                showPlayer = showPlayer,
                playerSheet = player,
                analysisSheet = analysis,
                content = { padding ->
                    contentPadding = padding
                    NavDisplay(
                        backStack = backStack,
                        onBack = { backStack.removeLastOrNull() },
                        entryProvider =
                            entryProvider {
                                entry<Albums> { Text("albums") }
                                entry<AlbumDetail> { Text("detail") }
                            },
                    )
                },
                miniPlayer = { Text("mini") },
                player = { Button(onClick = { scope.launch { player.collapse() } }) { Text("↓") } },
                analysisButton = { Text("analysis button") },
                analysis = { Text("analysis") },
            )
        }
    }

    private fun node(tag: String) = compose.onNodeWithTag(tag)

    private fun openBoth() {
        node(SheetTags.MINI_PLAYER).performClick()
        node(SheetTags.ANALYSIS_BUTTON).performClick()
        node(SheetTags.ANALYSIS).assertExists()
    }

    @Test
    fun tapMiniPlayerExpands() {
        setContent()
        node(SheetTags.MINI_PLAYER).performClick()
        node(SheetTags.PLAYER).assertExists()
        node(SheetTags.MINI_PLAYER).assertDoesNotExist()
    }

    @Test
    fun swipeUpExpandsAndSwipeDownCollapses() {
        setContent()
        node(SheetTags.MINI_PLAYER).performTouchInput { swipeUp() }
        node(SheetTags.PLAYER).assertExists()
        node(SheetTags.MINI_PLAYER).assertDoesNotExist()

        node(SheetTags.PLAYER).performTouchInput { swipeDown() }
        node(SheetTags.MINI_PLAYER).assertExists()
        node(SheetTags.PLAYER).assertDoesNotExist()
    }

    @Test
    fun downButtonCollapses() {
        setContent()
        node(SheetTags.MINI_PLAYER).performClick()
        compose.onNodeWithText("↓").performClick()
        node(SheetTags.MINI_PLAYER).assertExists()
    }

    @Test
    fun collapsingPlayerCollapsesAnalysis() {
        setContent()
        openBoth()
        // The open analysis sheet covers the button, so invoke its click directly.
        compose.onNodeWithText("↓").performSemanticsAction(SemanticsActions.OnClick)
        node(SheetTags.MINI_PLAYER).assertExists()

        node(SheetTags.MINI_PLAYER).performClick()
        node(SheetTags.ANALYSIS_BUTTON).assertExists()
        node(SheetTags.ANALYSIS).assertDoesNotExist()
    }

    @Test
    fun expandBeforeShownOpensExpanded() {
        setContent(show = false)
        compose.runOnIdle { scope.launch { player.expand() } }
        compose.runOnIdle { showPlayer = true }
        node(SheetTags.PLAYER).assertExists()
        node(SheetTags.MINI_PLAYER).assertDoesNotExist()
    }

    @Test
    fun hiddenPlayerHasNoPadding() {
        setContent(show = false)
        compose.runOnIdle { assertEquals(0.dp, contentPadding!!.calculateBottomPadding()) }

        compose.runOnIdle { showPlayer = true }
        compose.runOnIdle { assertTrue(contentPadding!!.calculateBottomPadding() >= 72.dp) }
    }
}
