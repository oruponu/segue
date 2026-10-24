package com.oruponu.segue.core

import kotlin.math.roundToInt

/**
 * Visuals data (core/spec/visuals-format.md). Spectrum and peaks are -50..0dB quantized to 0..255;
 * the waveform is the amplitude mapped to -127..127.
 */
class Visuals(
    val sampleRate: Int,
    val hop: Int,
    val frameCount: Int,
    val bandCount: Int,
    val spectrum: ByteArray,
    val peakLeft: ByteArray,
    val peakRight: ByteArray,
    val clipBits: ByteArray,
    val waveformRate: Int,
    val waveformCount: Int,
    val waveform: ByteArray,
) {
    fun spectrumLevel(frame: Int, band: Int): Int =
        spectrum[frame * bandCount + band].toInt() and 0xFF

    fun peakLeftLevel(frame: Int): Int = peakLeft[frame].toInt() and 0xFF

    fun peakRightLevel(frame: Int): Int = peakRight[frame].toInt() and 0xFF

    fun isClipped(frame: Int): Boolean = ((clipBits[frame / 8].toInt() shr (frame % 8)) and 1) == 1

    fun waveformMin(point: Int): Int = waveform[point * 2].toInt()

    fun waveformMax(point: Int): Int = waveform[point * 2 + 1].toInt()

    companion object {
        const val DB_FLOOR = -50f

        /** Same quantization as quantizeDb on the C++ side. */
        fun quantizeDb(db: Float): Int =
            ((db.coerceIn(DB_FLOOR, 0f) - DB_FLOOR) * 255f / -DB_FLOOR).roundToInt()

        fun levelToDb(level: Int): Float = level * -DB_FLOOR / 255f + DB_FLOOR
    }
}
