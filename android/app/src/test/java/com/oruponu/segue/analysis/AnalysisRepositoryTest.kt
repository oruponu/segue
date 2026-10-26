package com.oruponu.segue.analysis

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.oruponu.segue.analysis.TrackAnalysisState.Complete
import com.oruponu.segue.analysis.TrackAnalysisState.Failed
import com.oruponu.segue.analysis.TrackAnalysisState.Pending
import com.oruponu.segue.analysis.TrackAnalysisState.VisualsReady
import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.StyleScore
import com.oruponu.segue.data.TrackStoreLock
import com.oruponu.segue.data.db.AnalysisEntity
import com.oruponu.segue.data.db.AnalysisStatus
import com.oruponu.segue.data.db.SegueDatabase
import com.oruponu.segue.data.db.TrackDao
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.data.db.TrackStyleEntity
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val TIMEOUT_MS = 5_000L
private const val A = "Music/a.mp3"
private const val B = "Music/b.mp3"
private const val C = "Music/c.mp3"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalysisRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: SegueDatabase
    private lateinit var cache: VisualsCache
    private lateinit var modelStore: ModelStore
    private lateinit var worker: ExecutorCoroutineDispatcher
    private lateinit var scope: CoroutineScope
    private val errors: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
    private val engine = FakeAnalysisEngine()
    private val opener = FakeTrackFileOpener()
    private val lock = TrackStoreLock()
    private lateinit var trackDao: HookedTrackDao

    private val storedMusic =
        MusicResult(98f, 0.7f, 5, 1, 0.6f, listOf(StyleScore(3, 0.5f), StyleScore(7, 0.25f)))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SegueDatabase::class.java).build()
        trackDao = HookedTrackDao(db.trackDao())
        cache = VisualsCache(tmp.newFolder("visuals"), engine)
        val model = ByteArray(10)
        val asset =
            object : ModelAsset {
                override val length = model.size.toLong()

                override fun open(): InputStream = ByteArrayInputStream(model)
            }
        modelStore = ModelStore(asset, File(tmp.root, "models/m.onnx"))
        worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e })
    }

    @After
    fun tearDown() {
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        worker.close()
        db.close()
        assertEquals(emptyList<Throwable>(), errors.toList())
    }

    private fun repository(visualsCache: VisualsCache = cache) =
        AnalysisRepository(
            engine = engine,
            visualsCache = visualsCache,
            modelProvider = ModelProvider(modelStore, engine),
            opener = opener,
            trackDao = trackDao,
            analysisDao = db.analysisDao(),
            lock = lock,
            scope = scope,
            dispatcher = worker,
        )

    private fun track(key: String, size: Long = 100L) =
        TrackEntity(
            key = key,
            mediaStoreId = 1L,
            relativePath = key.substringBeforeLast('/') + "/",
            displayName = key.substringAfterLast('/'),
            title = key,
            album = "Album",
            artist = null,
            albumArtist = null,
            discNumber = null,
            trackNumber = null,
            durationMs = 1000L,
            size = size,
            dateModified = 10L,
            albumKey = "album",
        )

    private suspend fun addTracks(vararg keys: String) =
        db.trackDao().upsert(keys.map { track(it) })

    private suspend fun saveRow(
        key: String,
        status: AnalysisStatus = AnalysisStatus.OK,
        stylesDone: Boolean = true,
        version: Int = engine.analysisVersion,
    ) {
        val ok = status == AnalysisStatus.OK
        val m = storedMusic
        db.analysisDao()
            .save(
                AnalysisEntity(
                    trackKey = key,
                    analysisVersion = version,
                    status = status,
                    stylesDone = stylesDone,
                    bpm = m.bpm.takeIf { ok },
                    bpmConfidence = m.bpmConfidence.takeIf { ok },
                    keyNote = m.keyNote.takeIf { ok },
                    keyScale = m.keyScale.takeIf { ok },
                    keyConfidence = m.keyConfidence.takeIf { ok },
                    analyzedAt = 1L,
                ),
                if (ok) {
                    m.styles.mapIndexed { rank, s ->
                        TrackStyleEntity(key, rank, s.labelIndex, s.confidence)
                    }
                } else {
                    emptyList()
                },
            )
    }

    private fun writeCache(key: String) = cache.fileFor(key).writeBytes("SGVZ".toByteArray())

    private suspend fun row(key: String) = db.analysisDao().analysis(key)

    private suspend inline fun <reified T : TrackAnalysisState> AnalysisRepository.await(
        key: String,
    ): T = withTimeout(TIMEOUT_MS) { state(key).first { it is T } } as T

    /** Waits until the worker has run everything queued so far. */
    private suspend fun idle() = withContext(worker) {}

    /** Waits until the job that owns [token] has passed its finally block. */
    private suspend fun awaitDone(token: FakeAnalysisEngine.FakeToken) {
        assertTrue(token.closed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
        idle()
    }

    @Test
    fun cachedTrackCompletesWithoutDecode() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A)
            writeCache(A)
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(storedMusic, repo.await<Complete>(A).music)
            assertEquals(0, engine.decodes.get())
            assertEquals(0, opener.opened.get())
        }

    @Test
    fun noCacheDecodesWritesAndAnalyzes() =
        runBlocking<Unit> {
            addTracks(A)
            val gate = Gate()
            engine.onAnalyze = { gate.pass() }
            val repo = repository()
            assertEquals(Pending, repo.state(A).value)

            repo.setCurrent(A)
            gate.awaitEntered()
            val ready = repo.state(A).value as VisualsReady
            gate.release()
            val complete = repo.await<Complete>(A)
            idle()

            assertSame(ready.visuals, complete.visuals)
            assertEquals(engine.music, complete.music)
            val row = row(A)!!
            assertEquals(AnalysisStatus.OK, row.status)
            assertTrue(row.stylesDone)
            assertEquals(engine.analysisVersion, row.analysisVersion)
            assertTrue(cache.fileFor(A).exists())
            assertFalse(cache.tempFor(A).exists())
            assertTrue(engine.audios.all { it.closed.get() })
            assertEquals(0L, engine.tokens.single().closed.count)
            assertEquals(1, opener.closed.get())
        }

    @Test
    fun missingVisualsWithValidDbDecodesButSkipsAnalyze() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A)
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(storedMusic, repo.await<Complete>(A).music)
            assertEquals(1, engine.decodes.get())
            assertEquals(1, engine.writes.get())
            assertEquals(0, engine.analyzes.get())
            assertTrue(cache.fileFor(A).exists())
        }

    @Test
    fun missingVisualsWithStylesNotDoneAnalyzesWhenModelsLoad() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A, stylesDone = false)
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(engine.music, repo.await<Complete>(A).music)
            assertEquals(1, engine.analyzes.get())
            assertNotNull(engine.analyzeModels.single())
            assertTrue(row(A)!!.stylesDone)
        }

    @Test
    fun missingDbWithCacheEmitsVisualsFirstAndSkipsWrite() =
        runBlocking<Unit> {
            addTracks(A)
            writeCache(A)
            val gate = Gate()
            engine.onDecode = { gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            val ready = repo.state(A).value as VisualsReady
            gate.release()
            val complete = repo.await<Complete>(A)

            assertSame(ready.visuals, complete.visuals)
            assertEquals(0, engine.writes.get())
            assertEquals(1, engine.analyzes.get())
            assertEquals(AnalysisStatus.OK, row(A)!!.status)
        }

    @Test
    fun recordedFailureFailsWithoutDecode() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A, status = AnalysisStatus.DECODE_FAILED)
            writeCache(A)
            val repo = repository()

            repo.setCurrent(A)

            val failed = repo.await<Failed>(A)
            assertEquals(SegueStatus.DECODE_FAILED, failed.status)
            assertNotNull(failed.visuals)
            assertEquals(0, engine.decodes.get())
        }

    @Test
    fun oldAnalysisVersionIsIgnored() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A, version = engine.analysisVersion - 1)
            writeCache(A)
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(engine.music, repo.await<Complete>(A).music)
            assertEquals(1, engine.decodes.get())
            assertEquals(1, engine.analyzes.get())
            assertEquals(engine.analysisVersion, row(A)!!.analysisVersion)
        }

    @Test
    fun decodeFailureIsRecorded() =
        runBlocking<Unit> {
            addTracks(A)
            engine.onDecode = { throw SegueException(SegueStatus.DECODE_FAILED) }
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(Failed(SegueStatus.DECODE_FAILED, null), repo.await<Failed>(A))
            val row = row(A)!!
            assertEquals(AnalysisStatus.DECODE_FAILED, row.status)
            assertEquals(engine.analysisVersion, row.analysisVersion)
        }

    @Test
    fun ioFailureIsNotRecorded() =
        runBlocking<Unit> {
            addTracks(A)
            opener.error = SegueException(SegueStatus.IO_FAILED)
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(Failed(SegueStatus.IO_FAILED, null), repo.await<Failed>(A))
            idle()
            assertNull(row(A))
            assertEquals(0, engine.decodes.get())
        }

    @Test
    fun modelLoadFailureSavesWithoutStyles() =
        runBlocking<Unit> {
            addTracks(A, B, C)
            saveRow(C, stylesDone = false)
            writeCache(C)
            // Not a SegueException: any failure counts as the models being unavailable.
            engine.loadModelsError = IllegalStateException("broken model")
            val repo = repository()

            repo.setCurrent(A)
            repo.await<Complete>(A)
            repo.setCurrent(B)
            repo.await<Complete>(B)
            // Once the models are known to be unavailable, styles_done = false is good enough.
            repo.setCurrent(C)
            repo.await<Complete>(C)

            assertEquals(listOf<EngineModels?>(null, null), engine.analyzeModels.toList())
            val row = row(A)!!
            assertEquals(AnalysisStatus.OK, row.status)
            assertFalse(row.stylesDone)
            assertEquals(1, engine.modelLoads.get())
            assertEquals(2, engine.decodes.get())
        }

    @Test
    fun switchingTrackCancelsRunningJob() =
        runBlocking<Unit> {
            addTracks(A, B)
            val gate = Gate()
            engine.onDecode = { if (it == path(A)) gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            repo.setCurrent(B)
            assertTrue(engine.tokens[0].requested.get())
            gate.release()
            awaitDone(engine.tokens[0])

            assertNull(row(A))
            assertFalse(cache.fileFor(A).exists())
            assertFalse(cache.tempFor(A).exists())
            assertEquals(Pending, repo.state(A).value)
            repo.await<Complete>(B)
        }

    @Test
    fun staleGenerationDoesNotEmit() =
        runBlocking<Unit> {
            addTracks(A, B)
            val first = Gate()
            val second = Gate()
            val decodesOfA = AtomicInteger()
            engine.onDecode = {
                if (it == path(A)) {
                    if (decodesOfA.getAndIncrement() == 0) {
                        first.pass()
                        throw SegueException(SegueStatus.IO_FAILED)
                    }
                    second.pass()
                }
            }
            val repo = repository()
            val seen: MutableList<TrackAnalysisState> =
                Collections.synchronizedList(mutableListOf())
            val collector = launch(Dispatchers.Unconfined) { repo.state(A).collect { seen += it } }

            repo.setCurrent(A)
            first.awaitEntered()
            repo.setCurrent(B)
            repo.setCurrent(A)
            first.release()
            // The worker is single threaded, so the first job has finished once the third decodes.
            second.awaitEntered()
            assertEquals(Pending, repo.state(A).value)
            second.release()
            repo.await<Complete>(A)
            collector.cancel()

            assertTrue(seen.none { it is Failed })
        }

    @Test
    fun trackChangedDuringAnalysisIsNotSaved() =
        runBlocking<Unit> {
            addTracks(A)
            val gate = Gate()
            engine.onAnalyze = { gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            lock.withLock {
                db.trackDao().upsert(listOf(track(A, size = 200L)))
                db.analysisDao().deleteFor(listOf(A))
                cache.delete(listOf(A))
            }
            gate.release()
            awaitDone(engine.tokens.single())

            assertNull(row(A))
            assertFalse(cache.fileFor(A).exists())
            assertFalse(cache.tempFor(A).exists())
            assertTrue(repo.state(A).value is VisualsReady)
        }

    @Test
    fun trackDeletedDuringDecodeSavesNoVisuals() =
        runBlocking<Unit> {
            addTracks(A)
            val gate = Gate()
            engine.onDecode = { gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            lock.withLock {
                db.trackDao().delete(listOf(A))
                cache.delete(listOf(A))
            }
            gate.release()
            awaitDone(engine.tokens.single())

            assertFalse(cache.fileFor(A).exists())
            assertFalse(cache.tempFor(A).exists())
            assertEquals(0, engine.analyzes.get())
            assertEquals(Pending, repo.state(A).value)
        }

    @Test
    fun cancelledJobDoesNotSaveAfterNativeReturns() =
        runBlocking<Unit> {
            addTracks(A, B)
            val gate = Gate()
            engine.onAnalyze = { if (it == path(A)) gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            repo.setCurrent(B)
            gate.release()
            awaitDone(engine.tokens[0])

            assertNull(row(A))
            assertTrue(repo.state(A).value is VisualsReady)
            repo.await<Complete>(B)
            assertNotNull(row(B))
        }

    @Test
    fun currentTrackChangedBySyncIsReanalyzed() =
        runBlocking<Unit> {
            addTracks(A)
            val gate = Gate()
            engine.onDecode = { if (engine.decodes.get() == 2) gate.pass() }
            val repo = repository()
            repo.setCurrent(A)
            repo.await<Complete>(A)

            lock.withLock {
                db.trackDao().upsert(listOf(track(A, size = 200L)))
                db.analysisDao().deleteFor(listOf(A))
                cache.delete(listOf(A))
            }
            repo.onTracksChanged(listOf(A))
            assertEquals(Pending, repo.state(A).value)
            gate.awaitEntered()
            assertEquals(Pending, repo.state(A).value)
            gate.release()

            repo.await<Complete>(A)
            assertEquals(2, engine.decodes.get())
            assertNotNull(row(A))
        }

    @Test
    fun currentTrackRemovedBySyncFailsWithoutRecording() =
        runBlocking<Unit> {
            addTracks(A)
            val repo = repository()
            repo.setCurrent(A)
            repo.await<Complete>(A)

            lock.withLock {
                db.trackDao().delete(listOf(A))
                cache.delete(listOf(A))
            }
            repo.onTracksChanged(listOf(A))

            assertEquals(Failed(SegueStatus.IO_FAILED, null), repo.await<Failed>(A))
            idle()
            assertEquals(1, engine.decodes.get())
            assertNull(row(A))
        }

    @Test
    fun visualsSurviveCacheLimit() =
        runBlocking<Unit> {
            addTracks(A)
            val dir = tmp.newFolder("small")
            val gate = Gate()
            engine.onAnalyze = { gate.pass() }
            val repo = repository(VisualsCache(dir, engine, maxBytes = 1))

            repo.setCurrent(A)
            gate.awaitEntered()
            val ready = repo.state(A).value as VisualsReady
            gate.release()
            val complete = repo.await<Complete>(A)
            idle()

            assertSame(ready.visuals, complete.visuals)
            assertEquals(0, dir.listFiles { f -> f.name.endsWith(".sgv") }!!.size)
            assertEquals(0, dir.listFiles { f -> f.name.endsWith(".sgv.tmp") }!!.size)
        }

    @Test
    fun commitWaitsForTrackStoreLock() =
        runBlocking<Unit> {
            addTracks(A)
            val written = CountDownLatch(1)
            engine.onWriteVisuals = { written.countDown() }
            val repo = repository()
            val acquired = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            launch {
                lock.withLock {
                    acquired.complete(Unit)
                    withTimeoutOrNull(TIMEOUT_MS) { release.await() }
                }
            }
            acquired.await()

            repo.setCurrent(A)
            assertTrue(written.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))
            // Without the lock, the commit's byKey check would run in the same worker step as
            // writeVisuals, so idling the worker would let it happen.
            idle()
            assertEquals(1, trackDao.byKeyCalls.get())
            assertNull(row(A))
            assertFalse(cache.fileFor(A).exists())
            assertTrue(cache.tempFor(A).exists())
            assertEquals(Pending, repo.state(A).value)

            release.complete(Unit)
            repo.await<Complete>(A)
            assertNotNull(row(A))
            assertTrue(cache.fileFor(A).exists())
        }

    @Test
    fun retriedTrackStartsPending() =
        runBlocking<Unit> {
            addTracks(A, B)
            val gate = Gate()
            val decodesOfA = AtomicInteger()
            engine.onDecode = {
                if (it == path(A)) {
                    if (decodesOfA.getAndIncrement() == 0) {
                        throw SegueException(SegueStatus.IO_FAILED)
                    }
                    gate.pass()
                }
            }
            val repo = repository()
            repo.setCurrent(A)
            assertEquals(Failed(SegueStatus.IO_FAILED, null), repo.await<Failed>(A))
            repo.setCurrent(B)
            repo.await<Complete>(B)

            repo.setCurrent(A)
            gate.awaitEntered()
            assertEquals(Pending, repo.state(A).value)
            gate.release()

            repo.await<Complete>(A)
        }

    @Test
    fun nextJobWaitsForCancelledJobToFinish() =
        runBlocking<Unit> {
            addTracks(A, B)
            val parked = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            // The second byKey is the first job's commit check. Parking it where cancellation
            // cannot reach stands in for a slow Room call.
            trackDao.onByKey = { n ->
                if (n == 2) {
                    parked.complete(Unit)
                    withContext(NonCancellable) {
                        withTimeoutOrNull(TIMEOUT_MS) { release.await() }
                    }
                }
            }
            val repo = repository()

            repo.setCurrent(A)
            withTimeout(TIMEOUT_MS) { parked.await() }
            repo.setCurrent(B)
            repo.setCurrent(A)
            idle()
            // A new job for A would have read its snapshot in its first worker step.
            assertEquals(2, trackDao.byKeyCalls.get())

            release.complete(Unit)
            repo.await<Complete>(A)
            assertTrue(cache.fileFor(A).exists())
        }

    @Test
    fun unexpectedExceptionFailsWithoutRecording() =
        runBlocking<Unit> {
            addTracks(A)
            opener.error = IllegalStateException("provider failed")
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(Failed(SegueStatus.IO_FAILED, null), repo.await<Failed>(A))
            idle()
            assertNull(row(A))
            assertEquals(0L, engine.tokens.single().closed.count)
        }

    @Test
    fun sameCurrentKeyKeepsRunningJob() =
        runBlocking<Unit> {
            addTracks(A)
            val gate = Gate()
            engine.onDecode = { gate.pass() }
            val repo = repository()

            repo.setCurrent(A)
            gate.awaitEntered()
            repo.setCurrent(A)
            assertFalse(engine.tokens.single().requested.get())
            gate.release()
            repo.await<Complete>(A)
            idle()

            assertEquals(1, engine.decodes.get())
            assertEquals(1, engine.tokens.size)
        }

    @Test
    fun brokenCacheIsDeletedAndRedecoded() =
        runBlocking<Unit> {
            addTracks(A)
            saveRow(A)
            cache.fileFor(A).writeBytes("XXXX".toByteArray())
            val repo = repository()

            repo.setCurrent(A)

            assertEquals(storedMusic, repo.await<Complete>(A).music)
            assertEquals(1, engine.decodes.get())
            assertEquals(0, engine.analyzes.get())
            assertEquals("SGVZ", cache.fileFor(A).readText())
        }
}

private fun path(key: String) = "/fake/$key"

private class FakeTrackFileOpener : TrackFileOpener {
    val opened = AtomicInteger()
    val closed = AtomicInteger()
    @Volatile var error: Exception? = null

    override fun open(track: TrackEntity): OpenedTrack {
        error?.let { throw it }
        opened.incrementAndGet()
        return OpenedTrack(path(track.key), Closeable { closed.incrementAndGet() })
    }
}

/** Blocks the worker inside a fake native call until the test releases it. */
private class Gate {
    private val entered = CountDownLatch(1)
    private val released = CountDownLatch(1)

    fun pass() {
        entered.countDown()
        check(released.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "Gate was not released" }
    }

    fun awaitEntered() =
        check(entered.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "Gate was not entered" }

    fun release() = released.countDown()
}

private class HookedTrackDao(private val delegate: TrackDao) : TrackDao by delegate {
    val byKeyCalls = AtomicInteger()

    /** Runs before each byKey with its 1-based call number. */
    @Volatile var onByKey: suspend (Int) -> Unit = {}

    override suspend fun byKey(key: String): TrackEntity? {
        onByKey(byKeyCalls.incrementAndGet())
        return delegate.byKey(key)
    }
}
