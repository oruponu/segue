package com.oruponu.segue.playback

import androidx.datastore.core.DataStoreFactory
import androidx.media3.common.Player
import com.oruponu.segue.data.JsonSerializer
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaybackStateStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(file: File, scope: CoroutineScope) =
        PlaybackStateStore(
            DataStoreFactory.create(
                serializer = JsonSerializer(SavedPlayback.serializer(), SavedPlayback()),
                scope = scope,
            ) {
                file
            },
        )

    @Test
    fun savedStateRoundTrips() = runBlocking {
        val file = File(tmp.root, "datastore/playback.json")
        val state =
            SavedPlayback(
                queue = listOf("a", "b"),
                currentKey = "b",
                positionMs = 1234,
                shuffle = true,
                repeatMode = Player.REPEAT_MODE_ONE,
                speed = 1.5f,
                pitchSemitones = -3,
            )

        val writerJob = Job()
        store(file, CoroutineScope(Dispatchers.IO + writerJob)).save(state)
        // A second store on the same file is allowed only after the first one is closed.
        writerJob.cancelAndJoin()

        val readerJob = Job()
        try {
            assertEquals(
                state,
                store(file, CoroutineScope(Dispatchers.IO + readerJob)).saved.first(),
            )
        } finally {
            readerJob.cancelAndJoin()
        }
    }
}
