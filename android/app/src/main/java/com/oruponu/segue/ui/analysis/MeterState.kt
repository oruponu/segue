package com.oruponu.segue.ui.analysis

import com.oruponu.segue.core.Visuals
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

const val FLOOR_DB = -50f
const val SPECTRUM_DECAY_DB_PER_S = 25f
const val PEAK_DECAY_DB_PER_S = 26f
const val PEAK_HOLD_S = 1f
const val PEAK_HOLD_DECAY_DB_PER_S = 10f
const val CLIP_HOLD_S = 1f

class MeterTarget(
    val spectrumDb: FloatArray,
    val peakLeftDb: Float,
    val peakRightDb: Float,
    val clipped: Boolean,
)

/** Interpolates the dB values between the two frames around the position; the clip bit is not. */
fun Visuals.meterTargetAt(positionMs: Long): MeterTarget {
    val exact = positionMs / 1000.0 * sampleRate / hop
    val i0 = floor(exact).toInt().coerceIn(0, frameCount - 1)
    val i1 = min(i0 + 1, frameCount - 1)
    val t = (exact - i0).coerceIn(0.0, 1.0).toFloat()
    fun lerp(from: Int, to: Int): Float {
        val a = Visuals.levelToDb(from)
        return a + (Visuals.levelToDb(to) - a) * t
    }
    return MeterTarget(
        spectrumDb = FloatArray(bandCount) { lerp(spectrumLevel(i0, it), spectrumLevel(i1, it)) },
        peakLeftDb = lerp(peakLeftLevel(i0), peakLeftLevel(i1)),
        peakRightDb = lerp(peakRightLevel(i0), peakRightLevel(i1)),
        clipped = isClipped(i0),
    )
}

/** The meter values shown on screen, which rise at once and fall at the decay rates. */
class MeterState(bandCount: Int) {
    val spectrumDb = FloatArray(bandCount) { FLOOR_DB }
    var peakLeftDb = FLOOR_DB
        private set

    var peakRightDb = FLOOR_DB
        private set

    var holdLeftDb = FLOOR_DB
        private set

    var holdRightDb = FLOOR_DB
        private set

    var isActive = false
        private set

    val clipping: Boolean
        get() = clipTimer > 0

    private var holdLeftTimer = 0f
    private var holdRightTimer = 0f
    private var clipTimer = 0f

    /** A null target, as when paused, lets everything fall to [FLOOR_DB]. */
    fun update(target: MeterTarget?, dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0.001f, 0.1f)
        for (i in spectrumDb.indices) {
            val goal = target?.spectrumDb?.getOrNull(i) ?: FLOOR_DB
            spectrumDb[i] = approach(spectrumDb[i], goal, SPECTRUM_DECAY_DB_PER_S * dt)
        }
        val left = target?.peakLeftDb ?: FLOOR_DB
        val right = target?.peakRightDb ?: FLOOR_DB
        peakLeftDb = approach(peakLeftDb, left, PEAK_DECAY_DB_PER_S * dt)
        peakRightDb = approach(peakRightDb, right, PEAK_DECAY_DB_PER_S * dt)

        // A floor target leaves the timer running; otherwise a hold resting on the floor would
        // restart it every frame and never let the meter go idle.
        if (left > FLOOR_DB && left >= holdLeftDb) {
            holdLeftDb = left
            holdLeftTimer = PEAK_HOLD_S
        } else {
            holdLeftTimer -= dt
            if (holdLeftTimer <= 0) holdLeftDb = decayHold(holdLeftDb, dt)
        }
        if (right > FLOOR_DB && right >= holdRightDb) {
            holdRightDb = right
            holdRightTimer = PEAK_HOLD_S
        } else {
            holdRightTimer -= dt
            if (holdRightTimer <= 0) holdRightDb = decayHold(holdRightDb, dt)
        }

        if (target?.clipped == true) clipTimer = CLIP_HOLD_S else clipTimer -= dt

        isActive =
            spectrumDb.any { it > FLOOR_DB } ||
                peakLeftDb > FLOOR_DB ||
                peakRightDb > FLOOR_DB ||
                holdLeftDb > FLOOR_DB ||
                holdRightDb > FLOOR_DB ||
                holdLeftTimer > 0 ||
                holdRightTimer > 0 ||
                clipTimer > 0
        if (!isActive) reset()
    }

    private fun reset() {
        spectrumDb.fill(FLOOR_DB)
        peakLeftDb = FLOOR_DB
        peakRightDb = FLOOR_DB
        holdLeftDb = FLOOR_DB
        holdRightDb = FLOOR_DB
        holdLeftTimer = 0f
        holdRightTimer = 0f
        clipTimer = 0f
    }
}

private fun approach(current: Float, target: Float, fall: Float): Float =
    if (target >= current) target else max(current - fall, target)

private fun decayHold(hold: Float, dt: Float): Float =
    max(hold - PEAK_HOLD_DECAY_DB_PER_S * dt, FLOOR_DB)

/** Piecewise linear, so -10dB sits in the middle of the meter. */
fun peakMeterPosition(db: Float): Float =
    when {
        db <= -50f -> 0f
        db <= -30f -> (db + 50f) / 20f * 0.15f
        db <= -20f -> 0.15f + (db + 30f) / 10f * 0.15f
        db <= -10f -> 0.30f + (db + 20f) / 10f * 0.20f
        db <= 0f -> 0.50f + (db + 10f) / 10f * 0.50f
        else -> 1f
    }
