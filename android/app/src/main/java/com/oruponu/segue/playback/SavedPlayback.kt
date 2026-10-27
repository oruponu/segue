package com.oruponu.segue.playback

import kotlinx.serialization.Serializable

@Serializable
data class SavedPlayback(
    val queue: List<String> = emptyList(),
    val currentKey: String? = null,
    val currentIndex: Int = -1,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = 0,
    val speed: Float = 1f,
    val pitchSemitones: Int = 0,
)
