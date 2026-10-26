package com.oruponu.segue.playback

import androidx.datastore.core.DataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlaybackStateStore(private val dataStore: DataStore<SavedPlayback>) {
    val saved: Flow<SavedPlayback> = dataStore.data

    // DataStore runs each update in its caller's context and one at a time, so a save from the
    // main thread would wait for the main thread, which PlaybackService.onDestroy blocks while it
    // saves. One worker keeps saves in call order; it is never blocked, as updateData suspends
    // while it waits.
    private val writer = Dispatchers.IO.limitedParallelism(1)

    suspend fun save(state: SavedPlayback) {
        withContext(writer) { dataStore.updateData { state } }
    }
}
