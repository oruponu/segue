package com.oruponu.segue.library

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMediaStoreSourcePermissionTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val source = AndroidMediaStoreSource(context, Dispatchers.Unconfined)

    @Test
    fun queryWithoutPermissionThrows() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertThrows(SecurityException::class.java) { runBlocking { source.query("Music/") } }
    }

    @Test
    fun foldersWithoutPermissionThrows() {
        shadowOf(context).denyPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertThrows(SecurityException::class.java) { runBlocking { source.folders() } }
    }
}
