package com.oruponu.segue.library

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.oruponu.segue.analysis.AnalysisCanceller
import com.oruponu.segue.analysis.FakeAnalysisEngine
import com.oruponu.segue.analysis.VisualsCache
import com.oruponu.segue.data.TrackStoreLock
import com.oruponu.segue.data.db.AnalysisEntity
import com.oruponu.segue.data.db.AnalysisStatus
import com.oruponu.segue.data.db.SegueDatabase
import com.oruponu.segue.data.db.TrackStyleEntity
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: SegueDatabase
    private lateinit var cache: VisualsCache
    private val source = FakeMediaStoreSource()
    private val canceller = RecordingCanceller()
    private val settings = FakeDataStore(Settings(selectedFolder = "A/"))
    private val lock = TrackStoreLock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SegueDatabase::class.java).build()
        cache = VisualsCache(tmp.newFolder("visuals"), FakeAnalysisEngine())
    }

    @After fun tearDown() = db.close()

    private fun TestScope.repository() =
        LibraryRepository(
            source = source,
            settings = SettingsStore(settings),
            db = db,
            trackDao = db.trackDao(),
            analysisDao = db.analysisDao(),
            lock = lock,
            visualsCache = cache,
            canceller = canceller,
            scope = backgroundScope,
        )

    private fun track(
        id: Long,
        folder: String,
        name: String,
        size: Long = 100L,
        dateModified: Long = 10L,
    ) =
        MediaStoreTrack(
            id = id,
            displayName = name,
            relativePath = folder,
            title = name,
            album = "Album",
            artist = "Artist",
            albumArtist = null,
            cdTrackNumber = null,
            discNumber = null,
            durationMs = 1000L,
            size = size,
            dateModified = dateModified,
        )

    private fun tracks(folder: String, count: Int) =
        List(count) { track(it.toLong() + 1, folder, "t%03d.mp3".format(it)) }

    private val MediaStoreTrack.key
        get() = trackKey(relativePath, displayName)

    private suspend fun keysInDb() = db.trackDao().all().map { it.key }.toSet()

    private suspend fun addAnalysis(key: String) {
        db.analysisDao()
            .save(
                AnalysisEntity(key, 1, AnalysisStatus.OK, true, 120f, 0.9f, 0, 1, 0.8f, 100L),
                List(3) { TrackStyleEntity(key, it, it, 0.5f) },
            )
        cache.fileFor(key).writeBytes("SGVZ".toByteArray())
    }

    private suspend fun assertAnalysisKept(key: String) {
        assertNotNull(db.analysisDao().analysis(key))
        assertEquals(3, db.analysisDao().styles(key).size)
        assertTrue(cache.exists(key))
    }

    private suspend fun assertAnalysisDeleted(key: String) {
        assertNull(db.analysisDao().analysis(key))
        assertEquals(0, db.analysisDao().styles(key).size)
        assertFalse(cache.exists(key))
    }

    /**
     * Lets [repo] commit its first chunk, then holds the lock so that the sync waits before its
     * next step. Returns the sync job and the gate that releases the lock.
     */
    private suspend fun TestScope.pauseAfterFirstChunk(
        repo: LibraryRepository,
    ): Pair<Job, CompletableDeferred<Unit>> {
        val release1 = CompletableDeferred<Unit>()
        launch(start = CoroutineStart.UNDISPATCHED) { lock.withLock { release1.await() } }
        val sync = launch(start = CoroutineStart.UNDISPATCHED) { repo.sync() }
        // The mutex is fair, so this holder gets the lock right after the first chunk.
        val acquired2 = CompletableDeferred<Unit>()
        val release2 = CompletableDeferred<Unit>()
        launch(start = CoroutineStart.UNDISPATCHED) {
            lock.withLock {
                acquired2.complete(Unit)
                release2.await()
            }
        }
        release1.complete(Unit)
        acquired2.await()
        return sync to release2
    }

    @Test
    fun syncInsertsNewTracks() = runTest {
        val rows = listOf(track(1, "A/", "a.mp3"), track(2, "A/", "b.mp3"))
        source.tracks["A/"] = rows
        val repo = repository()

        repo.sync()

        assertEquals(rows.map { it.toEntity() }.toSet(), db.trackDao().all().toSet())
        assertEquals(listOf(2), repo.albums.first().map { it.tracks.size })
        assertEquals(SyncState.Idle, repo.syncState.value)
    }

    @Test
    fun unchangedTracksKeepAnalysis() = runTest {
        val a = track(1, "A/", "a.mp3")
        source.tracks["A/"] = listOf(a)
        val repo = repository()
        repo.sync()
        addAnalysis(a.key)

        repo.sync()

        assertAnalysisKept(a.key)
    }

    @Test
    fun changedSizeOrDateDeletesAnalysisAndCache() = runTest {
        val a = track(1, "A/", "a.mp3")
        val b = track(2, "A/", "b.mp3")
        source.tracks["A/"] = listOf(a, b)
        val repo = repository()
        repo.sync()
        addAnalysis(a.key)
        addAnalysis(b.key)

        source.tracks["A/"] = listOf(a.copy(size = 200L), b.copy(dateModified = 20L))
        repo.sync()

        assertEquals(200L, db.trackDao().byKey(a.key)!!.size)
        assertEquals(20L, db.trackDao().byKey(b.key)!!.dateModified)
        assertAnalysisDeleted(a.key)
        assertAnalysisDeleted(b.key)
    }

    @Test
    fun missingTrackIsDeletedWithAnalysisAndCache() = runTest {
        val a = track(1, "A/", "a.mp3")
        val b = track(2, "A/", "b.mp3")
        source.tracks["A/"] = listOf(a, b)
        val repo = repository()
        repo.sync()
        addAnalysis(b.key)

        source.tracks["A/"] = listOf(a)
        repo.sync()

        assertEquals(setOf(a.key), keysInDb())
        assertAnalysisDeleted(b.key)
    }

    @Test
    fun folderChangeReplacesLibrary() = runTest {
        source.tracks["A/"] = tracks("A/", 2)
        source.tracks["B/"] = listOf(track(10, "B/", "x.mp3"))
        val repo = repository()
        repo.sync()

        repo.selectFolder("B/")

        assertEquals("B/", repo.selectedFolder.first())
        assertEquals(setOf("B/x.mp3"), keysInDb())
    }

    @Test
    fun queryFailureKeepsLibrary() = runTest {
        source.tracks["A/"] = tracks("A/", 2)
        val repo = repository()
        repo.sync()
        val before = db.trackDao().all().toSet()

        source.queryError = SecurityException("no permission")
        repo.sync()

        assertEquals(before, db.trackDao().all().toSet())
        assertEquals(SyncState.PermissionDenied, repo.syncState.value)
    }

    @Test
    fun newerSyncDropsOlderSync() = runTest {
        source.tracks["A/"] = listOf(track(1, "A/", "old.mp3"))
        val gate = CompletableDeferred<Unit>()
        source.queryGate = gate
        val repo = repository()
        val older = launch(start = CoroutineStart.UNDISPATCHED) { repo.sync() }

        source.tracks["A/"] = listOf(track(2, "A/", "new.mp3"))
        repo.sync()
        gate.complete(Unit)
        older.join()

        assertEquals(setOf("A/new.mp3"), keysInDb())
    }

    @Test
    fun newerFolderSyncRemovesRowsOfPartialOlderSync() = runTest {
        source.tracks["A/"] = tracks("A/", 250)
        source.tracks["B/"] = tracks("B/", 3)
        val repo = repository()

        val (older, release) = pauseAfterFirstChunk(repo)
        assertEquals(200, db.trackDao().all().size)
        val newer = launch(start = CoroutineStart.UNDISPATCHED) { repo.selectFolder("B/") }
        release.complete(Unit)
        joinAll(older, newer)

        assertEquals(source.tracks.getValue("B/").map { it.key }.toSet(), keysInDb())
    }

    @Test
    fun duplicateKeysKeepFirst() = runTest {
        source.tracks["A/"] =
            listOf(track(5, "A/", "d.mp3"), track(9, "A/", "d.mp3").copy(title = "Other"))
        val repo = repository()

        repo.sync()

        val rows = db.trackDao().all()
        assertEquals(1, rows.size)
        assertEquals(5L, rows.single().mediaStoreId)
    }

    @Test
    fun syncNotifiesChangedTracks() = runTest {
        val changed = track(1, "A/", "changed.mp3")
        val removed = track(2, "A/", "removed.mp3")
        val same = track(3, "A/", "same.mp3")
        source.tracks["A/"] = listOf(changed, removed, same)
        val repo = repository()
        repo.sync()
        canceller.calls.clear()

        source.tracks["A/"] = listOf(changed.copy(size = 200L), same, track(4, "A/", "added.mp3"))
        repo.sync()

        assertEquals(setOf(changed.key, removed.key), canceller.calls.flatten().toSet())
    }

    @Test
    fun supersededSyncStillNotifiesCommittedChanges() = runTest {
        val a = track(1, "A/", "a.mp3")
        source.tracks["A/"] = listOf(a, track(2, "A/", "b.mp3"))
        val repo = repository()
        repo.sync()
        canceller.calls.clear()

        source.tracks["A/"] = listOf(a.copy(size = 200L), track(2, "A/", "b.mp3"))
        val (older, release) = pauseAfterFirstChunk(repo)
        assertTrue(older.isActive)
        assertEquals(200L, db.trackDao().byKey(a.key)!!.size)
        val newer = launch(start = CoroutineStart.UNDISPATCHED) { repo.sync() }
        release.complete(Unit)
        joinAll(older, newer)

        assertEquals(200L, db.trackDao().byKey(a.key)!!.size)
        assertTrue(canceller.calls.flatten().contains(a.key))
    }

    @Test
    fun autoSyncSurvivesSyncFailure() = runTest {
        source.tracks["A/"] = tracks("A/", 2)
        val repo = repository()
        source.queryError = SecurityException("no permission")
        repo.sync()
        assertEquals(SyncState.PermissionDenied, repo.syncState.value)

        source.queryError = IllegalStateException("MediaStore returned no cursor")
        repo.startAutoSync()
        runCurrent()
        assertEquals(2, source.queryCount)
        assertEquals(SyncState.Idle, repo.syncState.value)
        assertTrue(db.trackDao().all().isEmpty())

        source.queryError = null
        source.changes.tryEmit(Unit)
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(3, source.queryCount)
        assertEquals(2, db.trackDao().observeAll().first { it.isNotEmpty() }.size)
    }

    @Test
    fun syncWaitsForTrackStoreLock() = runTest {
        source.tracks["A/"] = tracks("A/", 2)
        val repo = repository()
        val release = CompletableDeferred<Unit>()
        val holder =
            launch(start = CoroutineStart.UNDISPATCHED) { lock.withLock { release.await() } }

        val sync = launch(start = CoroutineStart.UNDISPATCHED) { repo.sync() }
        assertTrue(db.trackDao().all().isEmpty())

        release.complete(Unit)
        joinAll(holder, sync)
        assertEquals(2, db.trackDao().all().size)
    }

    @Test
    fun progressReportsDoneAndTotal() = runTest {
        source.tracks["A/"] = tracks("A/", 450)
        val repo = repository()
        val states = mutableListOf<SyncState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.syncState.toList(states)
        }

        repo.sync()

        assertEquals(
            listOf(
                SyncState.Idle,
                SyncState.Syncing(200, 450),
                SyncState.Syncing(400, 450),
                SyncState.Syncing(450, 450),
                SyncState.Idle,
            ),
            states,
        )
    }

    @Test
    fun changesAreDebounced() = runTest {
        source.tracks["A/"] = tracks("A/", 1)
        val repo = repository()
        repo.startAutoSync()
        runCurrent()
        assertEquals(1, source.queryCount)

        repeat(3) {
            if (it > 0) advanceTimeBy(300)
            source.changes.tryEmit(Unit)
            runCurrent()
        }
        advanceTimeBy(999)
        runCurrent()
        assertEquals(1, source.queryCount)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, source.queryCount)
    }
}

private class FakeMediaStoreSource : MediaStoreSource {
    val tracks = mutableMapOf<String, List<MediaStoreTrack>>()
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 16)
    var queryError: Exception? = null
    /** When set, the next query takes its result and then waits for this gate. */
    var queryGate: CompletableDeferred<Unit>? = null
    var queryCount = 0

    override suspend fun folders(): List<String> = tracks.keys.sorted()

    override suspend fun query(folder: String): List<MediaStoreTrack> {
        queryCount++
        queryError?.let { throw it }
        val result = tracks[folder].orEmpty()
        queryGate?.let {
            queryGate = null
            it.await()
        }
        return result
    }

    override fun changes(): Flow<Unit> = changes
}

private class RecordingCanceller : AnalysisCanceller {
    val calls: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())

    override fun onTracksChanged(keys: Collection<String>) {
        calls += keys.toList()
    }
}

private class FakeDataStore(initial: Settings) : DataStore<Settings> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<Settings> = state

    override suspend fun updateData(transform: suspend (t: Settings) -> Settings): Settings =
        transform(state.value).also { state.value = it }
}
