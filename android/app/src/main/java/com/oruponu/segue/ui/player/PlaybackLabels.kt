package com.oruponu.segue.ui.player

import java.util.Locale
import kotlin.math.roundToInt

val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
val SEMITONE_PRESETS = -6..6

/** Rounds to hundredths and drops a trailing zero, but never below one decimal: "0.75x", "1.0x". */
fun speedLabel(speed: Float): String {
    val hundredths = (speed * 100).roundToInt()
    val format = if (hundredths % 10 == 0) "%.1fx" else "%.2fx"
    return String.format(Locale.ROOT, format, hundredths / 100.0)
}

fun semitoneLabel(semitones: Int): String = if (semitones > 0) "+$semitones" else "$semitones"
