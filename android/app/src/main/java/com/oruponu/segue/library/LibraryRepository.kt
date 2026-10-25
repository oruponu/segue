package com.oruponu.segue.library

import android.util.Log
import androidx.room.withTransaction
import com.oruponu.segue.analysis.AnalysisCanceller
import com.oruponu.segue.analysis.VisualsCache
import com.oruponu.segue.data.ApplicationScope
import com.oruponu.segue.data.TrackStoreLock
import com.oruponu.segue.data.db.AnalysisDao
import com.oruponu.segue.data.db.SegueDatabase
import com.oruponu.segue.data.db.TrackDao
import com.oruponu.segue.data.db.TrackEntity
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val TAG = "LibraryRepository"
private const val CHUNK_SIZE = 200
private const val CHANGE_DEBOUNCE_MS = 1000L

@Singleton
class LibraryRepository
@Inject
constructor(
    private val source: MediaStoreSource,
    private val settings: SettingsStore,
    private val db: SegueDatabase,
    private val trackDao: TrackDao,
    private val analysisDao: AnalysisDao,
    private val lock: TrackStoreLock,
    private val visualsCache: VisualsCache,
    private val canceller: AnalysisCanceller,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    val albums: Flow<List<Album>> = trackDao.observeAll().map(::groupAlbums)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    val selectedFolder: Flow<String?> = settings.selectedFolder

    private val generation = AtomicInteger()

    suspend fun folders(): List<String> = source.folders()

    suspend fun selectFolder(folder: String) {
        settings.setSelectedFolder(folder)
        sync()
    }

    suspend fun sync() {
        val gen = generation.incrementAndGet()
        try {
            sync(gen)
        } catch (e: Exception) {
            setState(gen, SyncState.Idle)
            throw e
        }
    }

    private suspend fun sync(gen: Int) {
        val folder = selectedFolder.first()
        if (folder == null) {
            setState(gen, SyncState.Idle)
            return
        }
        val rows =
            try {
                source.query(folder)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot read MediaStore", e)
                setState(gen, SyncState.PermissionDenied)
                return
            }
        val tracks = distinctTracks(rows)

        var done = 0
        for (chunk in tracks.chunked(CHUNK_SIZE)) {
            val changed =
                lock.withLock { if (gen == generation.get()) writeChunk(chunk) else null } ?: return
            // Notify per chunk so that committed changes are reported even if a newer sync
            // supersedes this one before it finishes.
            canceller.onTracksChanged(changed)
            done += chunk.size
            setState(gen, SyncState.Syncing(done, tracks.size))
        }

        val keys = tracks.mapTo(HashSet()) { it.key }
        val removed =
            lock.withLock {
                if (gen != generation.get()) return@withLock null
                // Compare with the rows now in the DB, which may include rows that an older
                // sync of another folder wrote partway.
                val removed = trackDao.all().map { it.key }.filterNot { it in keys }
                db.withTransaction { removed.chunked(CHUNK_SIZE).forEach { trackDao.delete(it) } }
                visualsCache.delete(removed)
                removed
            } ?: return
        canceller.onTracksChanged(removed)
        setState(gen, SyncState.Idle)
    }

    @OptIn(FlowPreview::class)
    fun startAutoSync() {
        scope.launch { selectedFolder.collect { syncLogged() } }
        scope.launch { source.changes().debounce(CHANGE_DEBOUNCE_MS).collect { syncLogged() } }
    }

    // A failure must not kill the application scope or stop later syncs.
    private suspend fun syncLogged() {
        try {
            sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Library sync failed", e)
        }
    }

    suspend fun track(key: String): TrackEntity? = trackDao.byKey(key)

    suspend fun tracks(keys: List<String>): List<TrackEntity> =
        keys.chunked(CHUNK_SIZE).flatMap { trackDao.byKeys(it) }

    // Keeps the first row of each key; rows come in _ID order.
    private fun distinctTracks(rows: List<MediaStoreTrack>): List<TrackEntity> {
        val tracks = LinkedHashMap<String, TrackEntity>()
        for (row in rows) {
            val entity = row.toEntity()
            val first = tracks.putIfAbsent(entity.key, entity)
            if (first != null) {
                Log.w(
                    TAG,
                    "Duplicate key ${entity.key}: keeping _ID ${first.mediaStoreId}, " +
                        "ignoring _ID ${entity.mediaStoreId}",
                )
            }
        }
        return tracks.values.toList()
    }

    private suspend fun writeChunk(chunk: List<TrackEntity>): List<String> {
        val current = trackDao.byKeys(chunk.map { it.key }).associateBy { it.key }
        val changed =
            chunk
                .filter { track ->
                    val old = current[track.key] ?: return@filter false
                    old.size != track.size || old.dateModified != track.dateModified
                }
                .map { it.key }
        db.withTransaction {
            analysisDao.deleteFor(changed)
            trackDao.upsert(chunk)
        }
        visualsCache.delete(changed)
        return changed
    }

    private fun setState(gen: Int, state: SyncState) {
        if (gen == generation.get()) _syncState.value = state
    }
}
