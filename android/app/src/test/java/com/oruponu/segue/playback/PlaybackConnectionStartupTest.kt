package com.oruponu.segue.playback

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.oruponu.segue.analysis.AnalysisCoordinator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Runs with the real application, whose onCreate also starts the app's coordinator. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackConnectionStartupTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun readingStateAndEventsDoesNotBindTheService() =
        runTest(UnconfinedTestDispatcher()) {
            val connection = PlaybackConnection(app)
            val keys = mutableListOf<String?>()
            AnalysisCoordinator(
                    connection.state.map { it.currentKey },
                    { keys += it },
                    backgroundScope,
                )
                .start()
            connection.events
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals(0, shadowOf(app).boundServiceConnections.size)
            assertEquals(0, shadowOf(app).unboundServiceConnections.size)
            assertEquals(listOf<String?>(null), keys)
        }

    @Test
    fun connectBindsTheService() {
        // No binder: the controller gives up, but the bind is what this checks.
        shadowOf(app)
            .setComponentNameAndServiceForBindService(
                ComponentName(app, PlaybackService::class.java),
                null,
            )
        val connection = PlaybackConnection(app)

        connection.connect()
        shadowOf(Looper.getMainLooper()).idle()

        val shadow = shadowOf(app)
        assertTrue(
            shadow.boundServiceConnections.isNotEmpty() ||
                shadow.unboundServiceConnections.isNotEmpty(),
        )
    }
}
