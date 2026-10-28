package com.oruponu.segue.ui.library

import com.oruponu.segue.library.Album
import com.oruponu.segue.library.SyncState

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val sync: SyncState = SyncState.Idle,
    val selectedFolder: String? = null,
    val loaded: Boolean = false,
)

enum class LibraryEmpty {
    NoFolder,
    EmptyFolder,
}

fun LibraryUiState.empty(): LibraryEmpty? =
    when {
        !loaded -> null
        selectedFolder == null -> LibraryEmpty.NoFolder
        albums.isEmpty() && !isSyncing -> LibraryEmpty.EmptyFolder
        else -> null
    }

val LibraryUiState.isSyncing: Boolean
    get() = sync is SyncState.Syncing
