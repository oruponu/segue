package com.oruponu.segue.ui.analysis

private val MajorNotes = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val MinorNotes = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")

/** Follows core/spec/key-notation.md; null when the note is outside 0-11. */
fun keyName(note: Int, scale: Int): String? {
    if (note !in 0..11) return null
    return if (scale == 1) "${MinorNotes[note]} Minor" else "${MajorNotes[note]} Major"
}
