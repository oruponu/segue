package com.oruponu.segue.library

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AndroidMediaStoreSourceTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)

    private val source =
        AndroidMediaStoreSource(
            InstrumentationRegistry.getInstrumentation().targetContext,
            Dispatchers.IO,
        )

    @Test
    fun foldersAreDistinctAndEndWithSlash() = runBlocking {
        val folders = source.folders()
        assumeTrue("No music on the device", folders.isNotEmpty())
        assertEquals(folders.distinct(), folders)
        assertTrue(folders.toString(), folders.all { it.endsWith("/") })
    }

    @Test
    fun queryReturnsTracksOfFolder() = runBlocking {
        val folders = source.folders()
        assumeTrue("No music on the device", folders.isNotEmpty())
        val folder = folders.first()
        val tracks = source.query(folder)
        assertTrue(tracks.isNotEmpty())
        assertTrue(tracks.all { it.relativePath.startsWith(folder) && it.size > 0 })
    }
}
