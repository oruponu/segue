package com.oruponu.segue.ui.library

import com.oruponu.segue.library.Album
import com.oruponu.segue.library.SyncState

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val sync: SyncState = SyncState.Idle,
    val loaded: Boolean = false,
)
