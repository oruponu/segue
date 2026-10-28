package com.oruponu.segue.playback

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.AndroidMediaStoreSource
import com.oruponu.segue.library.toEntity
import dagger.hilt.android.EntryPointAccessors
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val TIMEOUT_MS = 5_000L
private const val POSITION_TOLERANCE_MS = 500L

class PlaybackServiceTest {
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

    private lateinit var tracks: List<TrackEntity>
    private var insertedKeys = emptyList<String>()
    private var controller: MediaController? = null
    private val skipped = AtomicInteger()
    private val skippedSignal = Semaphore(0)

    @Before
    fun setUp() = runBlocking {
        val source = AndroidMediaStoreSource(context, Dispatchers.IO)
        tracks =
            source
                .folders()
                .flatMap { source.query(it) }
                .map { it.toEntity() }
                .distinctBy { it.key }
                .take(2)
        assumeTrue("Need 2 tracks on the device", tracks.size == 2)

        val keys = tracks.map { it.key }
        val present = trackDao.byKeys(keys).map { it.key }.toSet()
        insertedKeys = keys - present
        trackDao.upsert(tracks)
        // A state left by an earlier run must not be restored into this test.
        store.save(SavedPlayback())

        controller = connect()
        onMain { controller!!.resetSettings() }
    }

    @After
    fun tearDown() = runBlocking {
        controller?.let {
            onMain {
                it.stop()
                it.clearMediaItems()
                it.resetSettings()
                it.release()
            }
        }
        if (insertedKeys.isNotEmpty()) trackDao.delete(insertedKeys)
        store.save(SavedPlayback())
    }

    @Test
    fun playsDeviceTrack() {
        start(tracks.map { it.toMediaItem() })
        awaitPlayer("playing the first track") {
            it.isPlaying && it.currentMediaItem?.mediaId == tracks[0].key
        }
    }

    @Test
    fun pauseSavesState() = runBlocking {
        start(tracks.map { it.toMediaItem() })
        awaitPlayer("playing") { it.isPlaying }
        onMain { controller().seekToNextMediaItem() }
        awaitPlayer("playing the second track") {
            it.isPlaying && it.currentMediaItem?.mediaId == tracks[1].key
        }
        Thread.sleep(2_000)
        onMain { controller().pause() }

        val saved = awaitSaved("the pause") { it.currentKey == tracks[1].key && it.positionMs > 0 }
        assertEquals(tracks[1].key, saved.currentKey)
        assertTrue(saved.toString(), saved.positionMs > 0)
        assertEquals(tracks.map { it.key }, saved.queue)
    }

    @Test
    fun unplayableTrackIsSkipped() {
        val broken = tracks[0].copy(mediaStoreId = Long.MAX_VALUE)
        start(listOf(broken.toMediaItem(), tracks[1].toMediaItem()))
        awaitPlayer("playing the second track") {
            it.isPlaying && it.currentMediaItem?.mediaId == tracks[1].key
        }
        assertTrue(
            "SKIPPED_UNPLAYABLE not received",
            skippedSignal.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS),
        )
        assertEquals(1, skipped.get())
    }

    @Test
    fun allUnplayableWithRepeatAllStops() {
        val items = tracks.map { it.copy(mediaStoreId = Long.MAX_VALUE).toMediaItem() }
        onMain { controller().repeatMode = Player.REPEAT_MODE_ALL }
        awaitAllFailed(items)
        assertStaysStopped(items.lastIndex)
        assertEquals(1, skipped.get())
    }

    @Test
    fun newQueueAfterAllUnplayableStillSkips() {
        val bad = tracks.map { it.copy(mediaStoreId = Long.MAX_VALUE).toMediaItem() }
        awaitAllFailed(bad)
        skipped.set(0)
        skippedSignal.drainPermits()

        start(listOf(bad[0], tracks[1].toMediaItem()))
        awaitPlayer("playing the good track") {
            it.isPlaying && it.currentMediaItem?.mediaId == tracks[1].key
        }
        assertTrue(
            "SKIPPED_UNPLAYABLE not received",
            skippedSignal.tryAcquire(TIMEOUT_MS, TimeUnit.MILLISECONDS),
        )
    }

    @Test
    fun settingsChangedWhilePausedAreSaved() = runBlocking {
        start(tracks.map { it.toMediaItem() })
        awaitPlayer("playing") { it.isPlaying }
        onMain { controller().pause() }
        awaitPlayer("paused") { !it.isPlaying }
        // Saves are written in order, so later saves cannot be overtaken by the pause save.
        awaitSaved("the queue") { it.currentKey == tracks[0].key }

        // One change at a time: each must be saved by its own listener, as nothing else saves
        // while paused.
        onMain { controller().seekTo(1_000) }
        awaitSaved("the seek") { it.positionMs == 1_000L }
        onMain { controller().shuffleModeEnabled = true }
        awaitSaved("shuffle") { it.shuffle }
        onMain { controller().repeatMode = Player.REPEAT_MODE_ONE }
        awaitSaved("repeat") { it.repeatMode == Player.REPEAT_MODE_ONE }
        onMain { controller().playbackParameters = PlaybackParameters(1.5f, pitchRatio(2)) }
        val saved = awaitSaved("speed and pitch") { it.speed == 1.5f && it.pitchSemitones == 2 }

        assertEquals(
            SavedPlayback(
                queue = tracks.map { it.key },
                currentKey = tracks[0].key,
                currentIndex = 0,
                positionMs = 1_000,
                shuffle = true,
                repeatMode = Player.REPEAT_MODE_ONE,
                speed = 1.5f,
                pitchSemitones = 2,
            ),
            saved,
        )
    }

    @Test
    fun queueChangedWhilePausedIsSaved() = runBlocking {
        start(listOf(tracks[0].toMediaItem()))
        awaitPlayer("playing") { it.isPlaying }
        onMain { controller().pause() }
        awaitPlayer("paused") { !it.isPlaying }
        awaitSaved("the queue") { it.queue == listOf(tracks[0].key) }

        // Neither change moves to another item, so only the playlist change can save them.
        onMain { controller().addMediaItem(tracks[1].toMediaItem()) }
        awaitSaved("the added item") { it.queue == tracks.map { t -> t.key } }
        onMain { controller().moveMediaItem(1, 0) }
        val saved =
            awaitSaved("the moved item") { it.queue == tracks.reversed().map { t -> t.key } }
        assertEquals(tracks[0].key, saved.currentKey)
    }

    @Test
    fun startupRestoresSavedStatePaused() = runBlocking {
        val positionMs = (tracks[1].durationMs / 2).coerceAtMost(3_000)
        val saved =
            SavedPlayback(
                queue = tracks.map { it.key },
                currentKey = tracks[1].key,
                currentIndex = 1,
                positionMs = positionMs,
                shuffle = true,
                repeatMode = Player.REPEAT_MODE_ALL,
                speed = 1.25f,
                pitchSemitones = -3,
            )
        destroyService()
        store.save(saved)
        val written = Collections.synchronizedList(mutableListOf<SavedPlayback>())
        val watcher = launch(Dispatchers.IO) { store.saved.collect { written += it } }

        controller = connect()
        awaitPlayer("the restored queue") { it.mediaItemCount == tracks.size }

        assertRestored(saved.queue, tracks[1].key, positionMs, saved)
        // The restore saves what it restored; nothing else may be written.
        assertEquals(saved, store.saved.first())
        // Gives a stray write from an ungated save time to show up.
        delay(400)
        watcher.cancel()
        assertEquals(emptyList<SavedPlayback>(), written.filter { it != saved })
    }

    @Test
    fun startupRestoreSkipsDeletedCurrentTrack() = runBlocking {
        val deleted = tracks[0].copy(key = "SegueTest/deleted-${System.nanoTime()}.mp3")
        trackDao.upsert(listOf(deleted))
        trackDao.delete(listOf(deleted.key))
        val saved =
            SavedPlayback(
                queue = listOf(tracks[0].key, deleted.key, tracks[1].key),
                currentKey = deleted.key,
                positionMs = 4_000,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_ONE,
                speed = 0.75f,
                pitchSemitones = 2,
            )
        destroyService()
        store.save(saved)

        controller = connect()
        awaitPlayer("the restored queue") { it.mediaItemCount == tracks.size }

        val survivors = tracks.map { it.key }
        assertRestored(survivors, tracks[1].key, 0, saved)
        awaitSaved("the restored queue") {
            it ==
                saved.copy(
                    queue = survivors,
                    currentKey = tracks[1].key,
                    currentIndex = 1,
                    positionMs = 0,
                )
        }
        Unit
    }

    private fun controller(): MediaController = controller!!

    private fun start(items: List<MediaItem>) = onMain { controller().startWith(items) }

    private fun Player.startWith(items: List<MediaItem>) {
        setMediaItems(items)
        prepare()
        play()
    }

    private fun Player.resetSettings() {
        shuffleModeEnabled = false
        repeatMode = Player.REPEAT_MODE_OFF
        playbackParameters = PlaybackParameters.DEFAULT
    }

    /** [action] runs on the main thread after the listener is added, so no event is missed. */
    private fun awaitPlayer(
        what: String,
        timeoutMs: Long = TIMEOUT_MS,
        action: () -> Unit = {},
        condition: (Player) -> Boolean,
    ) {
        val latch = CountDownLatch(1)
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (condition(player)) latch.countDown()
                }
            }
        onMain {
            controller().addListener(listener)
            if (condition(controller())) latch.countDown()
            action()
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                val state = onMain {
                    controller().run {
                        "state=$playbackState playing=$isPlaying error=$playerError " +
                            "item=${currentMediaItem?.mediaId} skipped=${skipped.get()}"
                    }
                }
                fail("Timed out waiting for $what: $state")
            }
        } finally {
            onMain { controller().removeListener(listener) }
        }
    }

    private suspend fun awaitSaved(
        what: String,
        condition: (SavedPlayback) -> Boolean,
    ): SavedPlayback =
        withTimeoutOrNull(TIMEOUT_MS) { store.saved.first(condition) }
            ?: throw AssertionError(
                "Timed out waiting for $what to be saved: ${store.saved.first()}",
            )

    private fun awaitAllFailed(items: List<MediaItem>) {
        // The controller may merge quick updates, so an error on the last item is the signal.
        awaitPlayer(
            "an error on the last item",
            timeoutMs = 10_000,
            action = { controller().startWith(items) },
        ) {
            it.playbackState == Player.STATE_IDLE &&
                it.playerError != null &&
                it.currentMediaItemIndex == items.lastIndex
        }
    }

    private fun assertStaysStopped(index: Int) {
        val changes = Collections.synchronizedList(mutableListOf<String>())
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (
                        events.containsAny(
                            Player.EVENT_PLAYBACK_STATE_CHANGED,
                            Player.EVENT_MEDIA_ITEM_TRANSITION,
                        )
                    ) {
                        changes +=
                            "state=${player.playbackState} index=${player.currentMediaItemIndex}"
                    }
                }
            }
        onMain { controller().addListener(listener) }
        // A stop leaves no event to wait for, so give a retry (under 100 ms apart on the device)
        // time to show up.
        Thread.sleep(1_000)
        val (state, current) =
            onMain {
                controller().removeListener(listener)
                controller().run { playbackState to currentMediaItemIndex }
            }
        assertEquals(emptyList<String>(), changes.toList())
        assertEquals(Player.STATE_IDLE, state)
        assertEquals(index, current)
    }

    private fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    private fun connect(): MediaController {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val listener =
            object : MediaController.Listener {
                override fun onCustomCommand(
                    controller: MediaController,
                    command: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (command.customAction == PlaybackService.CUSTOM_COMMAND_SKIPPED_UNPLAYABLE) {
                        skipped.incrementAndGet()
                        skippedSignal.release()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
        val future = onMain {
            MediaController.Builder(context, token).setListener(listener).buildAsync()
        }
        return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun assertRestored(
        queue: List<String>,
        currentKey: String,
        positionMs: Long,
        saved: SavedPlayback,
    ) {
        val restored = onMain {
            controller().run {
                SavedPlayback(
                    queue = List(mediaItemCount) { getMediaItemAt(it).mediaId },
                    currentKey = currentMediaItem?.mediaId,
                    currentIndex = currentMediaItemIndex,
                    positionMs = currentPosition,
                    shuffle = shuffleModeEnabled,
                    repeatMode = repeatMode,
                    speed = playbackParameters.speed,
                    pitchSemitones = semitonesOf(playbackParameters.pitch),
                ) to playWhenReady
            }
        }
        val (state, playWhenReady) = restored
        assertEquals(
            saved.copy(
                queue = queue,
                currentKey = currentKey,
                currentIndex = queue.indexOf(currentKey),
                positionMs = state.positionMs,
            ),
            state,
        )
        assertTrue(
            "position ${state.positionMs}, expected $positionMs",
            abs(state.positionMs - positionMs) <= POSITION_TOLERANCE_MS,
        )
        assertFalse(playWhenReady)
    }

    /**
     * Destroys the service so that the next connection creates a new one, which restores the stored
     * state. Returns once its onDestroy has saved, so no older save can follow.
     */
    private fun destroyService() {
        // A controller on the session token itself does not bind the service, and it is
        // disconnected when onDestroy releases the session, after the final save.
        val sessionToken = onMain { controller().connectedToken!! }
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
        onMain {
            controller().release()
            controller = null
            releaseAppConnection()
        }
        context.stopService(Intent(context, PlaybackService::class.java))
        try {
            assertTrue(
                "PlaybackService was not destroyed",
                destroyed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS),
            )
        } finally {
            onMain { watcher.release() }
        }
    }

    // An earlier test class in this process may have left the app's connection bound, which would
    // keep the service alive. Called on the main thread, where disconnect() runs at once.
    private fun releaseAppConnection() = entryPoint.playbackConnection().disconnect()
}
