package com.oruponu.segue.playback

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow

class PlaybackStateStore(private val dataStore: DataStore<SavedPlayback>) {
    val saved: Flow<SavedPlayback> = dataStore.data

    suspend fun save(state: SavedPlayback) {
        dataStore.updateData { state }
    }
}
