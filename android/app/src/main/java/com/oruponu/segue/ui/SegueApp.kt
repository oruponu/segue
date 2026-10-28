package com.oruponu.segue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.oruponu.segue.library.SyncState
import com.oruponu.segue.ui.library.AlbumDetailScreen
import com.oruponu.segue.ui.library.AlbumListScreen
import com.oruponu.segue.ui.library.FolderPickerScreen
import com.oruponu.segue.ui.library.LibraryViewModel
import com.oruponu.segue.ui.library.album

@Composable
fun SegueApp() {
    val permission = rememberAudioPermissionState()
    val library: LibraryViewModel = hiltViewModel()
    val libraryState by library.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(if (permission.granted) Albums else Permission)

    LaunchedEffect(permission.granted) {
        val onPermission = backStack.lastOrNull() == Permission
        if (!permission.granted && !onPermission) {
            backStack.replaceWith(Permission)
        } else if (permission.granted && onPermission) {
            // A grant held at startup is left to the auto sync; only a grant made here syncs.
            backStack.replaceWith(Albums)
            library.onPermissionGranted()
        }
    }

    LaunchedEffect(libraryState.sync) {
        if (libraryState.sync is SyncState.PermissionDenied) permission.refresh()
    }

    NavDisplay(
        backStack = backStack,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<Albums> {
                    AlbumListScreen(
                        state = libraryState,
                        onAlbumClick = { backStack.add(AlbumDetail(it.key)) },
                        onPickFolder = {
                            if (backStack.lastOrNull() != FolderPicker) backStack.add(FolderPicker)
                        },
                    )
                }
                entry<AlbumDetail> { key ->
                    val album = libraryState.album(key.albumKey)
                    if (album != null) {
                        AlbumDetailScreen(
                            album = album,
                            currentKey = null,
                            isPlaying = false,
                            onTrackClick = {},
                            onBack = { backStack.removeLastOrNull() },
                        )
                    } else if (libraryState.loaded) {
                        LaunchedEffect(key) { backStack.remove(key) }
                    }
                }
                entry<FolderPicker> {
                    FolderPickerScreen(
                        onDone = { backStack.remove(FolderPicker) },
                        onPermissionLost = permission::refresh,
                    )
                }
                entry<Permission> { PermissionScreen(onGranted = permission::refresh) }
            },
    )
}

private fun NavBackStack<NavKey>.replaceWith(key: NavKey) {
    add(key)
    subList(0, size - 1).clear()
}
