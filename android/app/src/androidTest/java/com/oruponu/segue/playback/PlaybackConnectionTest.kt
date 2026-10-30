package com.oruponu.segue.playback

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.oruponu.segue.analysis.TrackAnalysisState
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.AndroidMediaStoreSource
import com.oruponu.segue.library.SyncState
import com.oruponu.segue.library.toEntity
import dagger.hilt.android.EntryPointAccessors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val TIMEOUT_MS = 5_000L
private const val ANALYSIS_TIMEOUT_MS = 60_000L
private const val SYNC_TIMEOUT_MS = 15_000L

class PlaybackConnectionTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS,
        )

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val entryPoint =
        EntryPointAccessors.fromApplication(context, PlaybackEntryPoint::class.java)
    private val trackDao = entryPoint.trackDao()
    private val store = entryPoint.playbackStateStore()
    private val connection = entryPoint.playbackConnection()
    private val visualsCache = entryPoint.visualsCache()
    private val source = AndroidMediaStoreSource(context, Dispatchers.IO)

    private lateinit var tracks: List<TrackEntity>
    private var keysBefore = emptySet<String>()
    private var insertedKeys = emptyList<String>()
    private var changedFolder = false
    private var folderBefore: String? = null
    private var controller: MediaController? = null

    @Before
    fun setUp() =
        runBlocking<Unit> {
            tracks =
                source
                    .folders()
                    .flatMap { source.query(it) }
                    .map { it.toEntity() }
                    .distinctBy { it.key }
                    .take(3)
            assumeTrue("Need 3 tracks on the device", tracks.size == 3)

            keysBefore = trackDao.all().mapTo(HashSet()) { it.key }
            insertedKeys = tracks.map { it.key } - keysBefore
            trackDao.upsert(tracks)
            // A state left by an earlier run must not be restored into this test.
            store.save(SavedPlayback())

            // A separate controller resets the session, as PlaybackConnection has no such
            // operation.
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = onMain { MediaController.Builder(context, token).buildAsync() }
            controller = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            onMain { controller!!.reset() }
            awaitState("the reset state") { it.isReset() }
        }

    @After
    fun tearDown() =
        runBlocking<Unit> {
            controller?.let {
                onMain {
                    it.reset()
                    it.release()
                }
                // Waits for the current track to clear, which stops its analysis before the rows
                // go.
                awaitState("the reset state") { state -> state.isReset() }
            }
            if (changedFolder) restoreLibrary()
            if (insertedKeys.isNotEmpty()) {
                trackDao.delete(insertedKeys)
                visualsCache.delete(insertedKeys)
            }
            store.save(SavedPlayback())
        }

    @Test
    fun playAlbumStartsAtTappedTrack() {
        connection.playAlbum(tracks, 1)
        val state =
            awaitState("playing the second track") {
                it.isPlaying && it.currentKey == tracks[1].key
            }
        assertTrue(state.hasNext)
        assertEquals(tracks[1].title, state.title)
        assertEquals(tracks[1].artist, state.artist)
    }

    @Test
    fun playbackContinuesAfterDisconnect() {
        connection.playAlbum(tracks, 0)
        awaitState("playing the first track") { it.isPlaying && it.currentKey == tracks[0].key }

        connection.disconnect()

        val state = awaitState("disconnected") { !it.connected }
        assertEquals(tracks[0].key, state.currentKey)
        assertEquals(null, state.connectedKey)
        assertTrue("The service stopped playing", onMain { controller!!.isPlaying })
    }

    @Test
    fun disconnectLetsTheServiceBeDestroyed() {
        connection.playAlbum(tracks, 0)
        awaitState("playing the first track") { it.isPlaying && it.currentKey == tracks[0].key }
        // A controller on the session token itself does not bind the service, and it is
        // disconnected when onDestroy releases the session.
        val sessionToken = onMain { controller!!.connectedToken!! }
        val destroyed = CountDownLatch(1)
        val watcher = onMain {
            MediaController.Builder(context, sessionToken)
                .setListener(
                    object : MediaController.Listener {
                        override fun onDisconnected(controller: MediaController) =
                            destroyed.countDown()
                    },
                )
                .buildAsync()
        }
            .get(TIMEOUT_MS, TimeUnit.MILLISECONDS)

        connection.disconnect()
        onMain {
            controller!!.reset()
            controller!!.release()
            controller = null
        }
        context.stopService(Intent(context, PlaybackService::class.java))

        try {
            assertTrue(
                "The app's connection kept the service alive",
                destroyed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
            )
        } finally {
            onMain { watcher.release() }
        }
    }

    @Test
    fun previousOnFirstTrackSeeksToStart() {
        connection.playAlbum(tracks, 0)
        awaitState("playing the first track") { it.isPlaying && it.currentKey == tracks[0].key }
        Thread.sleep(3_000)
        val beforeMs = SystemClock.elapsedRealtime()
        val played = connection.state.value
        val extrapolated =
            played.positionMs + ((beforeMs - played.positionAtElapsedMs) * played.speed).toLong()
        assertTrue("Position $extrapolated after 3 s of playback", extrapolated >= 2_500)

        connection.previous()
        val state =
            awaitState("the seek to the start") {
                it.positionAtElapsedMs >= beforeMs && it.positionMs < 1_000
            }
        assertEquals(tracks[0].key, state.currentKey)
    }

    @Test
    fun nextIsIgnoredOnLastTrack() {
        connection.playAlbum(tracks, 2)
        awaitState("playing the last track") { it.isPlaying && it.currentKey == tracks[2].key }

        connection.next()
        // Operations run in order, so once the speed shows, next() has been handled.
        connection.setSpeed(1.5f)
        val state = awaitState("the speed change") { it.speed == 1.5f }
        assertEquals(tracks[2].key, state.currentKey)
        assertFalse(state.hasNext)
    }

    @Test
    fun repeatAndShuffleAreReflected() {
        connection.cycleRepeat()
        awaitState("repeat all") { it.repeatMode == Player.REPEAT_MODE_ALL }
        connection.cycleRepeat()
        awaitState("repeat one") { it.repeatMode == Player.REPEAT_MODE_ONE }
        connection.cycleRepeat()
        awaitState("repeat off") { it.repeatMode == Player.REPEAT_MODE_OFF }
        connection.toggleShuffle()
        awaitState("shuffle") { it.shuffle }
    }

    @Test
    fun speedAndPitchAreClamped() {
        connection.setSpeed(3f)
        awaitState("the speed at its maximum") { it.speed == MAX_SPEED }
        connection.setPitchSemitones(-9)
        val state = awaitState("the pitch at its minimum") { it.pitchSemitones == MIN_SEMITONES }
        assertEquals(2f, state.speed)
        assertEquals(-6, state.pitchSemitones)
    }

    @Test
    fun unplayableTrackSendsEvent() =
        runBlocking<Unit> {
            val event =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    withTimeoutOrNull(TIMEOUT_MS) { connection.events.first() }
                }
            val broken = tracks[0].copy(mediaStoreId = Long.MAX_VALUE)
            connection.playAlbum(listOf(broken, tracks[1]), 0)

            assertEquals(PlaybackEvent.SkippedUnplayable, event.await())
            awaitState("playing the second track") {
                it.isPlaying && it.currentKey == tracks[1].key
            }
        }

    @Test
    fun endToEndAnalysis() =
        runBlocking<Unit> {
            val library = entryPoint.libraryRepository()
            val analysis = entryPoint.analysisRepository()
            val folder = library.folders().first()
            val folderKeys = source.query(folder).map { it.toEntity().key }.toSet()
            folderBefore = entryPoint.settingsStore().selectedFolder.first()
            changedFolder = true
            library.selectFolder(folder)
            // selectFolder can return early when the auto sync supersedes its sync. Until the sync
            // removes the tracks of other folders, such as those set up above, the first album
            // may be one about to be deleted.
            val albums =
                awaitFlow("the folder to be synced", library.albums, SYNC_TIMEOUT_MS) { albums ->
                    albums.flatMap { it.tracks }.mapTo(HashSet()) { it.key } == folderKeys
                }
            val album = albums.first()
            val key = album.tracks.first().key

            // Without cached visuals, the analysis must decode once even if the track has a result.
            visualsCache.delete(listOf(key))
            val sgvBefore = sgvFiles()
            val since = logcatTime()

            connection.playAlbum(album.tracks, 0)
            awaitState("the album's first track") { it.currentKey == key }
            val first =
                awaitFlow("the analysis", analysis.state(key), ANALYSIS_TIMEOUT_MS) {
                    it is TrackAnalysisState.Complete
                }
            assertEquals(setOf(visualsCache.fileFor(key).name), sgvFiles() - sgvBefore)
            assertEquals(1, decodeLogCount(key, since))

            // setCurrent(key) right after Complete may still see the finished job as running and do
            // nothing, so clearing first guarantees a second job. Only a job emits a new Complete.
            analysis.setCurrent(null)
            analysis.setCurrent(key)
            awaitFlow("the second analysis", analysis.state(key), ANALYSIS_TIMEOUT_MS) {
                it is TrackAnalysisState.Complete && it !== first
            }
            assertEquals(1, decodeLogCount(key, since))
        }

    private suspend fun restoreLibrary() {
        val library = entryPoint.libraryRepository()
        entryPoint.settingsStore().setSelectedFolder(folderBefore)
        // A sync of its own supersedes any sync of the test folder still running.
        library.sync()
        awaitFlow("the sync to end", library.syncState, SYNC_TIMEOUT_MS) { it == SyncState.Idle }
        if (folderBefore == null) {
            val synced = trackDao.all().map { it.key }.filterNot { it in keysBefore }
            trackDao.delete(synced)
            visualsCache.delete(synced)
        }
    }

    private fun MediaController.reset() {
        stop()
        clearMediaItems()
        shuffleModeEnabled = false
        repeatMode = Player.REPEAT_MODE_OFF
        playbackParameters = PlaybackParameters.DEFAULT
    }

    // A disconnected connection has already stopped the analysis and reads the reset session when
    // it connects again.
    private fun PlayerUiState.isReset(): Boolean =
        !connected ||
            currentKey == null &&
                !shuffle &&
                repeatMode == Player.REPEAT_MODE_OFF &&
                speed == 1f &&
                pitchSemitones == 0

    private fun awaitState(what: String, condition: (PlayerUiState) -> Boolean): PlayerUiState =
        runBlocking {
            awaitFlow(what, connection.state, TIMEOUT_MS, condition)
        }

    private suspend fun <T> awaitFlow(
        what: String,
        flow: Flow<T>,
        timeoutMs: Long = TIMEOUT_MS,
        condition: (T) -> Boolean,
    ): T =
        withTimeoutOrNull(timeoutMs) { flow.first(condition) }
            ?: throw AssertionError("Timed out waiting for $what: ${flow.first()}")

    private fun sgvFiles(): Set<String> =
        context.cacheDir
            .resolve("visuals")
            .listFiles { f -> f.name.endsWith(".sgv") }
            .orEmpty()
            .mapTo(HashSet()) { it.name }

    // Seconds since the epoch, a logcat -T format with no spaces to quote in executeShellCommand.
    private fun logcatTime(): String {
        val now = System.currentTimeMillis()
        return "%d.%03d".format(now / 1000, now % 1000)
    }

    /** Counts only lines logged since [since], so a rotated buffer cannot skew the count. */
    private fun decodeLogCount(key: String, since: String): Int {
        val output =
            instrumentation.uiAutomation.executeShellCommand(
                "logcat -d -T $since -s AnalysisRepository:D",
            )
        val log = ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().readText()
        return log.lines().count { it.trimEnd().endsWith("decode start $key") }
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
