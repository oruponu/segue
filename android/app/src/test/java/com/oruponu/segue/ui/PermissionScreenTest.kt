package com.oruponu.segue.ui

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import com.oruponu.segue.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PermissionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val results = ArrayDeque<Boolean>()
    private val registryOwner =
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry =
                object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: ActivityResultContract<I, O>,
                        input: I,
                        options: ActivityOptionsCompat?,
                    ) {
                        dispatchResult(requestCode, results.removeFirst())
                    }
                }
        }

    @Test
    fun secondDenialWithoutRationaleOpensSettings() {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                PermissionScreen(onGranted = {})
            }
        }
        val grant = compose.activity.getString(R.string.grant_permission)
        val shadow = shadowOf(compose.activity.packageManager)

        shadow.setShouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO, true)
        results += false
        compose.onNodeWithText(grant).performClick()
        compose.onNodeWithText(grant).assertExists()

        shadow.setShouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO, false)
        results += false
        compose.onNodeWithText(grant).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.open_settings)).assertExists()
    }
}
