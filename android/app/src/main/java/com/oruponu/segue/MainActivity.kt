package com.oruponu.segue

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.oruponu.segue.playback.PlaybackConnection
import com.oruponu.segue.ui.SegueApp
import com.oruponu.segue.ui.theme.SegueTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var connection: PlaybackConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Light system bar icons regardless of the device theme, as the app is always dark.
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent { SegueTheme { SegueApp() } }
    }

    override fun onStart() {
        super.onStart()
        connection.connect()
    }

    override fun onStop() {
        super.onStop()
        // Disconnecting on a configuration change would cancel and restart the running analysis.
        if (!isChangingConfigurations) connection.disconnect()
    }
}
