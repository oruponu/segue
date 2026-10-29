package com.oruponu.segue.ui.analysis

import android.content.res.AssetManager
import kotlinx.serialization.json.Json

private const val STYLE_LABELS = "style-labels.json"

private val MajorNotes = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
private val MinorNotes = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")

/** Follows core/spec/key-notation.md; null when the note is outside 0-11. */
fun keyName(note: Int, scale: Int): String? {
    if (note !in 0..11) return null
    return if (scale == 1) "${MinorNotes[note]} Minor" else "${MajorNotes[note]} Major"
}

/** Names styles from "<genre>---<style>" labels, adding the genre where a style repeats. */
class StyleLabels(labels: List<String>) {
    private val names: List<String>

    init {
        val parts = labels.map { label ->
            val split = label.split("---")
            split[0] to (split.getOrNull(1) ?: split[0])
        }
        val repeated = parts.groupingBy { it.second }.eachCount().filterValues { it > 1 }.keys
        names = parts.map { (genre, style) -> if (style in repeated) "$style ($genre)" else style }
    }

    val size: Int
        get() = names.size

    fun displayName(index: Int): String = names[index]
}

fun loadStyleLabels(assets: AssetManager): StyleLabels {
    val text = assets.open(STYLE_LABELS).bufferedReader().use { it.readText() }
    return StyleLabels(Json.decodeFromString<List<String>>(text))
}
