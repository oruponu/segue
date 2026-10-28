package com.oruponu.segue.playback

import androidx.media3.common.Player

data class PlayerUiState(
    val currentKey: String? = null,
    val isPlaying: Boolean = false,
    val isEnded: Boolean = false,
    val positionMs: Long = 0,
    /** SystemClock.elapsedRealtime() when [positionMs] was read. */
    val positionAtElapsedMs: Long = 0,
    val durationMs: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val speed: Float = 1f,
    val pitchSemitones: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
)

sealed interface PlaybackEvent {
    data object SkippedUnplayable : PlaybackEvent
}
