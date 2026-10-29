package com.oruponu.segue.ui.player

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val launched = mutableListOf<Any?>()
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
                        launched += input
                        dispatchResult(requestCode, false)
                    }
                }
        }

    private fun request(): () -> Unit {
        lateinit var request: () -> Unit
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                request = rememberNotificationPermissionRequest()
            }
        }
        return request
    }

    @Test
    fun requestsOnlyOnce() {
        val request = request()
        compose.runOnIdle {
            request()
            request()
        }
        assertEquals(listOf<Any?>(Manifest.permission.POST_NOTIFICATIONS), launched)
    }

    @Test
    fun grantedDoesNotRequest() {
        shadowOf(compose.activity.application)
            .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val request = request()
        compose.runOnIdle { request() }
        assertEquals(emptyList<Any?>(), launched)
    }
}
