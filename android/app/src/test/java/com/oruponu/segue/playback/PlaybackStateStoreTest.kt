package com.oruponu.segue.playback

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.media3.common.Player
import com.oruponu.segue.data.JsonSerializer
import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val TIMEOUT_MS = 5_000L
private const val SLOW_MS = 200L

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

    @Test
    fun blockingSaveIsNotStuckBehindSaveFromSameThread() = runBlocking {
        val job = Job()
        val store = store(File(tmp.root, "datastore/playback.json"), CoroutineScope(IO + job))
        val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        // Equal to the stored state, so it runs its update without writing: on Windows, DataStore
        // cannot replace an existing file.
        val first = SavedPlayback()
        val last = SavedPlayback(currentKey = "last")
        try {
            // Like PlaybackService: a save launched on the main thread, then onDestroy blocking
            // that thread on its final save.
            val saved =
                CompletableFuture.supplyAsync(
                    {
                        CoroutineScope(main).launch(start = CoroutineStart.UNDISPATCHED) {
                            store.save(first)
                        }
                        runBlocking { withTimeoutOrNull(TIMEOUT_MS) { store.save(last) } }
                    },
                    main.executor,
                )
            assertNotNull("The blocking save timed out", saved.get())
            assertEquals(last, store.saved.first())
        } finally {
            main.close()
            job.cancelAndJoin()
        }
    }

    @Test
    fun savesKeepCallOrder() = runBlocking {
        val dataStore = RecordingDataStore()
        val store = PlaybackStateStore(dataStore)
        val main = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val states = List(3) { SavedPlayback(positionMs = it.toLong()) }
        try {
            // Launched back to back on the main thread, as the player listeners do.
            val saves =
                withContext(main) {
                    states.map {
                        CoroutineScope(main).launch(start = CoroutineStart.UNDISPATCHED) {
                            store.save(it)
                        }
                    }
                }
            withTimeout(TIMEOUT_MS) { saves.joinAll() }
            assertEquals(states, dataStore.calls.toList())
        } finally {
            main.close()
        }
    }
}

/** Records the order of updates. The first state is slow to arrive, like a preempted worker. */
private class RecordingDataStore : DataStore<SavedPlayback> {
    val calls: MutableList<SavedPlayback> = Collections.synchronizedList(mutableListOf())

    override val data: Flow<SavedPlayback> = emptyFlow()

    override suspend fun updateData(
        transform: suspend (t: SavedPlayback) -> SavedPlayback,
    ): SavedPlayback {
        val state = transform(SavedPlayback(positionMs = -1))
        if (state.positionMs == 0L) Thread.sleep(SLOW_MS)
        calls += state
        return state
    }
}
