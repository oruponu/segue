package com.oruponu.segue.ui.library

import com.oruponu.segue.library.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryUiStateTest {
    @Test
    fun notLoadedShowsNothing() {
        assertNull(LibraryUiState().empty())
    }

    @Test
    fun noFolderSelected() {
        assertEquals(LibraryEmpty.NoFolder, LibraryUiState(loaded = true).empty())
    }

    @Test
    fun emptyFolderAfterSync() {
        val state = LibraryUiState(selectedFolder = "A/", loaded = true)
        assertEquals(LibraryEmpty.EmptyFolder, state.empty())
    }

    @Test
    fun syncingIsNotEmpty() {
        val state =
            LibraryUiState(
                sync = SyncState.Syncing(0, 10),
                selectedFolder = "A/",
                loaded = true,
            )
        assertNull(state.empty())
    }
}
