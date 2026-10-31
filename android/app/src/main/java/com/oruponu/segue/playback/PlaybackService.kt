package com.oruponu.segue.playback

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionCommand
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.oruponu.segue.R
import com.oruponu.segue.data.db.TrackDao
import com.oruponu.segue.data.db.byKeysChunked
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private const val TAG = "PlaybackService"
private const val SAVE_INTERVAL_MS = 10_000L

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var stateStore: PlaybackStateStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private lateinit var session: MediaLibrarySession
    private var ticker: Job? = null
    private var consecutiveErrors = 0

    // Saving before the restore has read the stored state, or after it failed to, would overwrite
    // the stored queue with an empty one.
    private var restore = RestoreState.PENDING

    private enum class RestoreState {
        PENDING,
        DONE,
        FAILED,
    }

    private class Restored(
        val saved: SavedPlayback,
        val items: List<MediaItem>,
        val queue: RestoredQueue,
    )

    override fun onCreate() {
        super.onCreate()
        player =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true,
                )
                .setHandleAudioBecomingNoisy(true)
                .build()
        player.addListener(PlayerListener())
        session =
            MediaLibrarySession.Builder(this, player, SessionCallback())
                .setBitmapLoader(ThumbnailBitmapLoader(this))
                .build()
        scope.launch { restoreIfEmpty() }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        session

    override fun onDestroy() {
        ticker?.cancel()
        if (canSave()) {
            val state = player.snapshot()
            runBlocking { saveOrLog(state) }
        }
        scope.cancel()
        player.release()
        session.release()
        super.onDestroy()
    }

    private suspend fun restoreIfEmpty() {
        restore =
            try {
                val restored = loadRestored()
                if (restored != null && player.mediaItemCount == 0) {
                    applySettings(restored.saved)
                    player.setMediaItems(
                        restored.items,
                        restored.queue.startIndex,
                        restored.queue.startPositionMs,
                    )
                    player.prepare()
                    player.playWhenReady = false
                }
                RestoreState.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cannot restore the playback state", e)
                RestoreState.FAILED
            }
        // A controller may have set items while the restore was reading.
        if (player.mediaItemCount > 0) save()
    }

    private suspend fun loadRestored(): Restored? {
        val saved = stateStore.saved.first()
        val items = libraryItems(saved.queue)
        val queue = restoreQueue(saved, items.keys) ?: return null
        return Restored(saved, queue.keys.map(items::getValue), queue)
    }

    /** Keys not in the library are left out. */
    private suspend fun libraryItems(keys: List<String>): Map<String, MediaItem> {
        val unknownArtist = getString(R.string.unknown_artist)
        return trackDao.byKeysChunked(keys).associate { it.key to it.toMediaItem(unknownArtist) }
    }

    private fun <T> future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope
            .launch {
                try {
                    future.set(block())
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            .invokeOnCompletion { cause -> cause?.let(future::setException) }
        return future
    }

    private fun applySettings(saved: SavedPlayback) {
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeatMode
        player.playbackParameters =
            PlaybackParameters(saved.speed, pitchRatio(saved.pitchSemitones))
    }

    private fun canSave(): Boolean =
        when (restore) {
            RestoreState.PENDING -> false
            RestoreState.DONE -> true
            RestoreState.FAILED -> player.mediaItemCount > 0
        }

    private fun save() {
        if (!canSave()) return
        val state = player.snapshot()
        scope.launch { saveOrLog(state) }
    }

    private suspend fun saveOrLog(state: SavedPlayback) {
        try {
            stateStore.save(state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Cannot save the playback state", e)
        }
    }

    private fun Player.snapshot(): SavedPlayback =
        SavedPlayback(
            queue = List(mediaItemCount) { getMediaItemAt(it).mediaId },
            currentKey = currentMediaItem?.mediaId,
            currentIndex = currentMediaItemIndex,
            positionMs = currentPosition,
            shuffle = shuffleModeEnabled,
            repeatMode = repeatMode,
            speed = playbackParameters.speed,
            pitchSemitones = semitonesOf(playbackParameters.pitch),
        )

    private inner class PlayerListener : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = save()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            ticker?.cancel()
            if (isPlaying) {
                ticker = scope.launch {
                    while (true) {
                        delay(SAVE_INTERVAL_MS)
                        save()
                    }
                }
            } else {
                save()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = save()

        override fun onRepeatModeChanged(repeatMode: Int) = save()

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = save()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) save()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            consecutiveErrors = 0
            // Adding or moving an item other than the current one saves nothing else.
            save()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) consecutiveErrors = 0
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.w(TAG, "Cannot play ${player.currentMediaItem?.mediaId}", error)
            consecutiveErrors++
            // Without the limit, repeat-all over a queue of unplayable tracks would loop forever.
            if (player.hasNextMediaItem() && consecutiveErrors < player.mediaItemCount) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
                session.broadcastCustomCommand(
                    SessionCommand(CUSTOM_COMMAND_SKIPPED_UNPLAYABLE, Bundle.EMPTY),
                    Bundle.EMPTY,
                )
            } else {
                player.stop()
            }
        }
    }

    // The service is exported, so any app can connect and send items. Only the media ID is taken
    // from them; the URI and metadata come from the library.
    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = future {
            val items = libraryItems(mediaItems.map { it.mediaId })
            mediaItems.mapNotNull { items[it.mediaId] }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val keys = mediaItems.map { it.mediaId }
            if (keys.isEmpty()) {
                return@future MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
            }
            val items = libraryItems(keys)
            // Failing keeps the current queue, where an empty result would clear it.
            val queue =
                keepExisting(keys, startIndex, startPositionMs, items.keys)
                    ?: throw IllegalArgumentException("No requested track is in the library")
            // An unset index asks for the default start, which dropping a track does not change.
            val unset = startIndex == C.INDEX_UNSET
            MediaItemsWithStartPosition(
                queue.keys.map(items::getValue),
                if (unset) C.INDEX_UNSET else queue.startIndex,
                if (unset) startPositionMs else queue.startPositionMs,
            )
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> = future {
            val restored = loadRestored() ?: throw IllegalStateException("No saved track remains")
            if (isForPlayback) applySettings(restored.saved)
            MediaItemsWithStartPosition(
                restored.items,
                restored.queue.startIndex,
                restored.queue.startPositionMs,
            )
        }
    }

    companion object {
        const val CUSTOM_COMMAND_SKIPPED_UNPLAYABLE = "com.oruponu.segue.SKIPPED_UNPLAYABLE"
    }
}
