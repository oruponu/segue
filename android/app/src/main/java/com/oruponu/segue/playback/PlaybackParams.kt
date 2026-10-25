package com.oruponu.segue.playback

import androidx.media3.common.Player
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

const val MIN_SPEED = 0.5f
const val MAX_SPEED = 2.0f
const val MIN_SEMITONES = -6
const val MAX_SEMITONES = 6

fun pitchRatio(semitones: Int): Float = 2.0.pow(semitones / 12.0).toFloat()

fun semitonesOf(pitch: Float): Int = (12 * log2(pitch.toDouble())).roundToInt()

fun nextRepeatMode(mode: Int): Int =
    when (mode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
    }
