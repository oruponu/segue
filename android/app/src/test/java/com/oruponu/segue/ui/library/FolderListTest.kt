package com.oruponu.segue.ui.library

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric, as the failure path logs through android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolderListTest {
    @Test
    fun foldersAreLoaded() = runTest {
        assertEquals(
            FolderListState.Loaded(listOf("A/", "B/")),
            loadFolderList { listOf("A/", "B/") },
        )
    }

    @Test
    fun noFoldersIsEmpty() = runTest {
        assertEquals(FolderListState.Empty, loadFolderList { emptyList() })
    }

    @Test
    fun securityExceptionIsPermissionDenied() = runTest {
        assertEquals(
            FolderListState.PermissionDenied,
            loadFolderList { throw SecurityException("READ_MEDIA_AUDIO is not granted") },
        )
    }

    @Test
    fun otherFailureIsFailed() = runTest {
        assertEquals(FolderListState.Failed, loadFolderList { error("x") })
    }

    @Test
    fun cancellationIsRethrown() {
        assertThrows(CancellationException::class.java) {
            runBlocking { loadFolderList { throw CancellationException() } }
        }
    }
}
