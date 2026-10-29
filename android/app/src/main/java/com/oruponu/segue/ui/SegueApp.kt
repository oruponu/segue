package com.oruponu.segue.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.oruponu.segue.R
import com.oruponu.segue.library.SyncState
import com.oruponu.segue.ui.library.AlbumDetailScreen
import com.oruponu.segue.ui.library.AlbumListScreen
import com.oruponu.segue.ui.library.FolderPickerScreen
import com.oruponu.segue.ui.library.LibraryViewModel
import com.oruponu.segue.ui.library.album
import com.oruponu.segue.ui.player.MiniPlayer
import com.oruponu.segue.ui.player.PlaybackCallbacks
import com.oruponu.segue.ui.player.PlayerScreen
import com.oruponu.segue.ui.player.PlayerViewModel
import com.oruponu.segue.ui.player.rememberNotificationPermissionRequest
import com.oruponu.segue.ui.player.rememberPositionMs
import com.oruponu.segue.ui.sheet.MainLayout
import com.oruponu.segue.ui.sheet.rememberSheetState
import kotlinx.coroutines.launch

private val AnalysisButtonHeight = 56.dp

@Composable
fun SegueApp() {
    val permission = rememberAudioPermissionState()
    val library: LibraryViewModel = hiltViewModel()
    val libraryState by library.state.collectAsStateWithLifecycle()
    val player: PlayerViewModel = hiltViewModel()
    val playerState by player.state.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(if (permission.granted) Albums else Permission)
    val playerSheet = rememberSheetState()
    val analysisSheet = rememberSheetState()
    val scope = rememberCoroutineScope()
    val requestNotifications = rememberNotificationPermissionRequest()
    val callbacks =
        remember(player) {
            PlaybackCallbacks(
                onPlayPause = player::togglePlayPause,
                onPrevious = player::previous,
                onNext = player::next,
                onSeek = player::seekTo,
                onShuffle = player::toggleShuffle,
                onRepeat = player::cycleRepeat,
                onSpeed = player::setSpeed,
                onPitch = player::setPitch,
            )
        }
    // Read inside the sheet slots only, so the frame-by-frame position recomposes just them.
    val position = rememberPositionMs(playerState.playback)

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

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MainLayout(
            showPlayer = playerState.playback.currentKey != null,
            playerSheet = playerSheet,
            analysisSheet = analysisSheet,
            content = { padding ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
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
                                        if (backStack.lastOrNull() != FolderPicker) {
                                            backStack.add(FolderPicker)
                                        }
                                    },
                                )
                            }
                            entry<AlbumDetail> { key ->
                                val album = libraryState.album(key.albumKey)
                                if (album != null) {
                                    AlbumDetailScreen(
                                        album = album,
                                        currentKey = playerState.playback.currentKey,
                                        isPlaying = playerState.playback.isPlaying,
                                        onTrackClick = { index ->
                                            requestNotifications()
                                            player.play(album.tracks, index)
                                            scope.launch { playerSheet.expand() }
                                        },
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
            },
            miniPlayer = {
                MiniPlayer(
                    ui = playerState,
                    positionMs = position.value,
                    onPlayPause = player::togglePlayPause,
                    onNext = player::next,
                )
            },
            player = {
                PlayerScreen(
                    ui = playerState,
                    positionMs = position.value,
                    callbacks = callbacks,
                    onCollapse = { scope.launch { playerSheet.collapse() } },
                )
            },
            analysisButton = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TextButton(
                        onClick = { scope.launch { analysisSheet.expand() } },
                        modifier = Modifier.height(AnalysisButtonHeight),
                    ) {
                        Icon(painterResource(R.drawable.ic_analytics), contentDescription = null)
                        Text(stringResource(R.string.analysis), Modifier.padding(start = 8.dp))
                    }
                    Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                }
            },
            // TODO: Replace this placeholder with the analysis screen.
            analysis = { Box(Modifier.fillMaxSize()) },
        )
    }
}

private fun NavBackStack<NavKey>.replaceWith(key: NavKey) {
    add(key)
    subList(0, size - 1).clear()
}
