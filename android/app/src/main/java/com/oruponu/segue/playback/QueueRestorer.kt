package com.oruponu.segue.playback

data class RestoredQueue(val keys: List<String>, val startIndex: Int, val startPositionMs: Long)

fun restoreQueue(saved: SavedPlayback, existing: Set<String>): RestoredQueue? {
    // The index tells repeated occurrences of a track apart; the key alone still restores a state
    // saved without it.
    val current =
        saved.currentIndex.takeIf { saved.queue.getOrNull(it) == saved.currentKey }
            ?: saved.queue.indexOf(saved.currentKey)
    return keepExisting(saved.queue, current, saved.positionMs, existing)
}

/**
 * Drops tracks that no longer exist. If the track at [startIndex] is gone, playback starts at
 * position 0 of the first survivor after it, or of the last survivor before it.
 */
fun keepExisting(
    queue: List<String>,
    startIndex: Int,
    startPositionMs: Long,
    existing: Set<String>,
): RestoredQueue? {
    val survivors = queue.withIndex().filter { it.value in existing }
    if (survivors.isEmpty()) return null
    val keys = survivors.map { it.value }
    val kept = survivors.indexOfFirst { it.index == startIndex }
    if (kept >= 0) return RestoredQueue(keys, kept, startPositionMs)
    val next = survivors.indexOfFirst { it.index > startIndex }
    return RestoredQueue(keys, if (next >= 0) next else survivors.lastIndex, 0)
}
