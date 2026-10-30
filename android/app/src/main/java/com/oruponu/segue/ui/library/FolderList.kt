package com.oruponu.segue.ui.library

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "FolderList"

sealed interface FolderListState {
    data object Loading : FolderListState

    data class Loaded(val folders: List<String>) : FolderListState

    data object Empty : FolderListState

    data object Failed : FolderListState

    data object PermissionDenied : FolderListState
}

suspend fun loadFolderList(fetch: suspend () -> List<String>): FolderListState =
    try {
        val folders = fetch()
        if (folders.isEmpty()) FolderListState.Empty else FolderListState.Loaded(folders)
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        FolderListState.PermissionDenied
    } catch (e: Exception) {
        Log.e(TAG, "Cannot load the music folders", e)
        FolderListState.Failed
    }
