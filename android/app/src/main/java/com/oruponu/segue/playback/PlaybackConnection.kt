package com.oruponu.segue.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.oruponu.segue.data.db.TrackEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PlaybackConnection"

/**
 * The app's MediaController on [PlaybackService]. It connects on [connect] or the first operation;
 * reading [state] or [events] does not connect, so a process start alone neither binds the service
 * nor starts an analysis. [state] stays at the default until the controller connects. Operations
 * may be called from any thread; they run on the main thread, and those issued before the
 * controller connects run once it does.
 */
@Singleton
class PlaybackConnection @Inject constructor(@ApplicationContext private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    // Touched only on the main thread.
    private var controller: MediaController? = null
    private var connecting: ListenableFuture<MediaController>? = null
    private val pending = ArrayDeque<(MediaController) -> Unit>()

    /** Connects unless already connected or connecting. May be called from any thread. */
    fun connect() = onMain(::startConnecting)

    /**
     * Releases the controller, drops operations waiting for it and resets [state]. Playback goes on
     * in the service. The next [connect] or operation connects again. May be called from any
     * thread.
     */
    fun disconnect() = onMain {
        pending.clear()
        connecting?.let(MediaController::releaseFuture)
        connecting = null
        controller?.release()
        controller = null
        _state.value = PlayerUiState()
    }

    fun playAlbum(tracks: List<TrackEntity>, startIndex: Int) {
        val items = tracks.map { it.toMediaItem() }
        perform {
            it.setMediaItems(items, startIndex, 0)
            it.prepare()
            it.play()
        }
    }

    fun togglePlayPause() = perform {
        when {
            it.playbackState == Player.STATE_ENDED -> {
                it.seekTo(0)
                it.play()
            }
            it.playWhenReady -> it.pause()
            else -> {
                // After an error or stop, play() alone does nothing.
                if (it.playbackState == Player.STATE_IDLE) it.prepare()
                it.play()
            }
        }
    }

    fun next() = perform { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }

    fun previous() = perform {
        if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem() else it.seekTo(0)
    }

    fun seekTo(positionMs: Long) = perform { it.seekTo(positionMs) }

    fun toggleShuffle() = perform { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = perform { it.repeatMode = nextRepeatMode(it.repeatMode) }

    fun setSpeed(speed: Float) = perform {
        it.playbackParameters =
            PlaybackParameters(speed.coerceIn(MIN_SPEED, MAX_SPEED), it.playbackParameters.pitch)
    }

    fun setPitchSemitones(semitones: Int) = perform {
        it.playbackParameters =
            PlaybackParameters(
                it.playbackParameters.speed,
                pitchRatio(semitones.coerceIn(MIN_SEMITONES, MAX_SEMITONES)),
            )
    }

    private fun perform(op: (MediaController) -> Unit) = onMain {
        val current = controller
        if (current != null) {
            op(current)
        } else {
            pending.addLast(op)
            startConnecting()
        }
    }

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun startConnecting() {
        if (controller != null || connecting != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future =
            MediaController.Builder(context, token).setListener(ControllerListener()).buildAsync()
        connecting = future
        future.addListener({ onConnected(future) }, context.mainExecutor)
    }

    private fun onConnected(future: ListenableFuture<MediaController>) {
        // disconnect() released this future, or a newer connection replaced it.
        if (connecting !== future) return
        connecting = null
        val connected =
            try {
                future.get()
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "Cannot connect to the playback service; dropping ${pending.size} operations",
                    e,
                )
                pending.clear()
                return
            }
        controller = connected
        connected.addListener(PlayerListener())
        _state.value = connected.toUiState()
        while (pending.isNotEmpty()) pending.removeFirst()(connected)
    }

    private fun Player.toUiState(): PlayerUiState {
        val position = currentPosition
        val readAt = SystemClock.elapsedRealtime()
        return PlayerUiState(
            currentKey = currentMediaItem?.mediaId,
            isPlaying = isPlaying,
            isEnded = playbackState == Player.STATE_ENDED,
            positionMs = position,
            positionAtElapsedMs = readAt,
            // C.TIME_UNSET is negative.
            durationMs = duration.coerceAtLeast(0),
            title = currentMediaItem?.mediaMetadata?.title?.toString(),
            artist = currentMediaItem?.mediaMetadata?.artist?.toString(),
            speed = playbackParameters.speed,
            pitchSemitones = semitonesOf(playbackParameters.pitch),
            shuffle = shuffleModeEnabled,
            repeatMode = repeatMode,
            hasNext = hasNextMediaItem(),
        )
    }

    private inner class PlayerListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = player.toUiState()
        }
    }

    private inner class ControllerListener : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction == PlaybackService.CUSTOM_COMMAND_SKIPPED_UNPLAYABLE) {
                _events.tryEmit(PlaybackEvent.SkippedUnplayable)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onDisconnected(controller: MediaController) {
            // The state returns to the default, so the coordinator cancels the analysis. Nothing
            // reconnects until the next connect() or operation.
            if (this@PlaybackConnection.controller === controller) {
                this@PlaybackConnection.controller = null
                _state.value = PlayerUiState()
            }
            controller.release()
        }
    }
}
