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
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
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
        val tracks = trackDao.byKeysChunked(saved.queue).associateBy { it.key }
        val queue = restoreQueue(saved, tracks.keys) ?: return null
        return Restored(saved, queue.keys.map { tracks.getValue(it).toMediaItem() }, queue)
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

    private inner class SessionCallback : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { it.withResolvedUri() })

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaItemsWithStartPosition> =
            Futures.immediateFuture(
                MediaItemsWithStartPosition(
                    mediaItems.map { it.withResolvedUri() },
                    startIndex,
                    startPositionMs,
                ),
            )

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaItemsWithStartPosition>()
            scope
                .launch {
                    try {
                        val restored = loadRestored()
                        if (restored == null) {
                            future.setException(IllegalStateException("No saved track remains"))
                        } else {
                            if (isForPlayback) applySettings(restored.saved)
                            future.set(
                                MediaItemsWithStartPosition(
                                    restored.items,
                                    restored.queue.startIndex,
                                    restored.queue.startPositionMs,
                                ),
                            )
                        }
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
                .invokeOnCompletion { cause -> cause?.let(future::setException) }
            return future
        }
    }

    companion object {
        const val CUSTOM_COMMAND_SKIPPED_UNPLAYABLE = "com.oruponu.segue.SKIPPED_UNPLAYABLE"
    }
}
