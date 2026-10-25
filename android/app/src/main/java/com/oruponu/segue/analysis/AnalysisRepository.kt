package com.oruponu.segue.analysis

import android.util.Log
import com.oruponu.segue.analysis.TrackAnalysisState.Complete
import com.oruponu.segue.analysis.TrackAnalysisState.Failed
import com.oruponu.segue.analysis.TrackAnalysisState.Pending
import com.oruponu.segue.analysis.TrackAnalysisState.VisualsReady
import com.oruponu.segue.core.MusicResult
import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.core.StyleScore
import com.oruponu.segue.core.Visuals
import com.oruponu.segue.data.AnalysisDispatcher
import com.oruponu.segue.data.ApplicationScope
import com.oruponu.segue.data.TrackStoreLock
import com.oruponu.segue.data.db.AnalysisDao
import com.oruponu.segue.data.db.AnalysisEntity
import com.oruponu.segue.data.db.AnalysisStatus
import com.oruponu.segue.data.db.TrackDao
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.data.db.TrackStyleEntity
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AnalysisRepository"

@Singleton
class AnalysisRepository
@Inject
constructor(
    private val engine: AnalysisEngine,
    private val visualsCache: VisualsCache,
    private val modelProvider: ModelProvider,
    private val opener: TrackFileOpener,
    private val trackDao: TrackDao,
    private val analysisDao: AnalysisDao,
    private val lock: TrackStoreLock,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:AnalysisDispatcher private val dispatcher: CoroutineDispatcher,
) : AnalysisCanceller {
    private val states = ConcurrentHashMap<String, MutableStateFlow<TrackAnalysisState>>()

    // Guards generation, currentKey and running. Never held across a suspension.
    private val guard = Any()
    private var generation = 0L
    private var currentKey: String? = null
    private var running: Task? = null

    // Held for a job's whole run. A cancelled job can still be suspended (in Room or on the lock)
    // and its finally deletes the key's temp visuals, so the next job must not start before it
    // ends.
    private val sequence = Mutex()

    fun state(key: String): StateFlow<TrackAnalysisState> = flowFor(key).asStateFlow()

    fun setCurrent(key: String?) {
        synchronized(guard) {
            if (key != null && key == currentKey && running?.key == key) return
            currentKey = key
            start(key)
        }
    }

    override fun onTracksChanged(keys: Collection<String>) {
        synchronized(guard) {
            val restart = running?.key in keys || currentKey in keys
            keys.forEach { flowFor(it).value = Pending }
            // A deleted track ends its new job at the snapshot step, so there is no need to check
            // here whether the current track still exists.
            if (restart) start(currentKey)
        }
    }

    private fun flowFor(key: String): MutableStateFlow<TrackAnalysisState> =
        states.computeIfAbsent(key) { MutableStateFlow(Pending) }

    private fun start(key: String?) {
        val gen = ++generation
        running?.stop()
        running = null
        if (key == null) return
        // Drops a state left by an earlier job, such as an unrecorded failure being retried.
        flowFor(key).value = Pending
        val task = Task(key, gen)
        task.job = scope.launch(dispatcher) { sequence.withLock { runTask(task) } }
        running = task
    }

    private fun emit(task: Task, state: TrackAnalysisState) {
        synchronized(guard) { if (task.gen == generation) flowFor(task.key).value = state }
    }

    private suspend fun runTask(task: Task) {
        try {
            analyze(task)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot analyze ${task.key}", e)
            emit(task, Failed(SegueStatus.IO_FAILED, task.visuals))
        } finally {
            synchronized(guard) { if (running === task) running = null }
        }
    }

    private suspend fun analyze(task: Task) {
        val key = task.key
        val snapshot = trackDao.byKey(key) ?: return
        val row = analysisDao.analysis(key)?.takeIf { it.analysisVersion == engine.analysisVersion }
        val cached = visualsCache.read(key)
        task.visuals = cached
        if (row != null && row.status != AnalysisStatus.OK) {
            emit(task, Failed(row.status.toSegueStatus(), cached))
            return
        }
        val usableRow = row?.takeIf { it.stylesDone || modelProvider.unavailable }
        if (usableRow != null && cached != null) {
            emit(task, Complete(cached, storedMusic(usableRow)))
            return
        }
        if (cached != null) emit(task, VisualsReady(cached))

        val token = engine.newToken()
        task.attach(token)
        try {
            val audio = opener.open(snapshot).use { engine.decode(it.path, token) }
            audio.use {
                val ready =
                    cached
                        ?: run {
                            engine.writeVisuals(audio, visualsCache.tempFor(key).path)
                            val committed = commitVisuals(snapshot) ?: return
                            task.visuals = committed
                            emit(task, VisualsReady(committed))
                            committed
                        }
                if (usableRow != null) {
                    emit(task, Complete(ready, storedMusic(usableRow)))
                    return
                }
                val models = modelProvider.models()
                val music = engine.analyze(audio, models, token)
                if (!commitResult(snapshot, music, stylesDone = models != null)) return
                emit(task, Complete(ready, music))
            }
        } catch (e: SegueException) {
            if (e.status == SegueStatus.DECODE_FAILED || e.status == SegueStatus.ANALYSIS_FAILED) {
                commitFailure(snapshot, e.status)
            }
            emit(task, Failed(e.status, task.visuals))
        } finally {
            task.detach()
            token.close()
            visualsCache.discardTemp(key)
        }
    }

    private suspend fun storedMusic(row: AnalysisEntity): MusicResult =
        row.toMusicResult(analysisDao.styles(row.trackKey))

    private suspend fun commitVisuals(snapshot: TrackEntity): Visuals? =
        commit(snapshot) {
            visualsCache.commit(snapshot.key) ?: throw SegueException(SegueStatus.IO_FAILED)
        }

    private suspend fun commitResult(
        snapshot: TrackEntity,
        music: MusicResult,
        stylesDone: Boolean,
    ): Boolean =
        commit(snapshot) {
            val key = snapshot.key
            analysisDao.save(
                AnalysisEntity(
                    trackKey = key,
                    analysisVersion = engine.analysisVersion,
                    status = AnalysisStatus.OK,
                    stylesDone = stylesDone,
                    bpm = music.bpm,
                    bpmConfidence = music.bpmConfidence,
                    keyNote = music.keyNote,
                    keyScale = music.keyScale,
                    keyConfidence = music.keyConfidence,
                    analyzedAt = System.currentTimeMillis(),
                ),
                music.styles.mapIndexed { rank, style ->
                    TrackStyleEntity(key, rank, style.labelIndex, style.confidence)
                },
            )
        } != null

    private suspend fun commitFailure(snapshot: TrackEntity, status: SegueStatus) {
        commit(snapshot) {
            analysisDao.save(
                AnalysisEntity(
                    trackKey = snapshot.key,
                    analysisVersion = engine.analysisVersion,
                    status = status.toAnalysisStatus(),
                    stylesDone = false,
                    bpm = null,
                    bpmConfidence = null,
                    keyNote = null,
                    keyScale = null,
                    keyConfidence = null,
                    analyzedAt = System.currentTimeMillis(),
                ),
                emptyList(),
            )
        }
    }

    /**
     * Runs [write] under the lock only if the track still matches [snapshot]; otherwise discards
     * the temp visuals and returns null. ensureActive() stops a cancelled job whose native call
     * returned before it saw the token.
     */
    private suspend fun <T : Any> commit(snapshot: TrackEntity, write: suspend () -> T): T? =
        lock.withLock {
            currentCoroutineContext().ensureActive()
            val current = trackDao.byKey(snapshot.key)
            if (
                current == null ||
                    current.size != snapshot.size ||
                    current.dateModified != snapshot.dateModified
            ) {
                visualsCache.discardTemp(snapshot.key)
                return@withLock null
            }
            write()
        }

    private class Task(val key: String, val gen: Long) {
        lateinit var job: Job

        // The latest visuals this job has, touched only on the worker.
        var visuals: Visuals? = null
        private var token: EngineToken? = null
        private var stopped = false

        fun attach(token: EngineToken) =
            synchronized(this) {
                this.token = token
                if (stopped) token.request()
            }

        fun detach() = synchronized(this) { token = null }

        fun stop() {
            synchronized(this) {
                stopped = true
                token?.request()
            }
            job.cancel()
        }
    }
}

fun AnalysisEntity.toMusicResult(styles: List<TrackStyleEntity>): MusicResult =
    MusicResult(
        bpm = bpm ?: 0f,
        bpmConfidence = bpmConfidence ?: 0f,
        keyNote = keyNote ?: -1,
        keyScale = keyScale ?: 0,
        keyConfidence = keyConfidence ?: 0f,
        styles = styles.sortedBy { it.rank }.map { StyleScore(it.labelIndex, it.confidence) },
    )

private fun AnalysisStatus.toSegueStatus(): SegueStatus =
    when (this) {
        AnalysisStatus.OK -> SegueStatus.OK
        AnalysisStatus.DECODE_FAILED -> SegueStatus.DECODE_FAILED
        AnalysisStatus.ANALYSIS_FAILED -> SegueStatus.ANALYSIS_FAILED
    }

private fun SegueStatus.toAnalysisStatus(): AnalysisStatus =
    when (this) {
        SegueStatus.DECODE_FAILED -> AnalysisStatus.DECODE_FAILED
        SegueStatus.ANALYSIS_FAILED -> AnalysisStatus.ANALYSIS_FAILED
        else -> throw IllegalArgumentException("$this is not recorded")
    }
