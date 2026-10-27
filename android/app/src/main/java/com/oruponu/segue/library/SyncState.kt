package com.oruponu.segue.library

sealed interface SyncState {
    data object Idle : SyncState

    data class Syncing(val done: Int, val total: Int) : SyncState

    data object PermissionDenied : SyncState
}
