package com.oruponu.segue.ui.library

import com.oruponu.segue.library.Album

data class LibraryUiState(
    val albums: List<Album> = emptyList(),
    val loaded: Boolean = false,
)
