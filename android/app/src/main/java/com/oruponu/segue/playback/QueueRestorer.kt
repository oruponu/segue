package com.oruponu.segue.playback

data class RestoredQueue(val keys: List<String>, val startIndex: Int, val startPositionMs: Long)

/**
 * Drops tracks that no longer exist. If the saved current track is gone, playback starts at
 * position 0 of the first survivor after it, or of the last survivor before it.
 */
fun restoreQueue(saved: SavedPlayback, existing: Set<String>): RestoredQueue? {
    val survivors = saved.queue.withIndex().filter { it.value in existing }
    if (survivors.isEmpty()) return null
    val keys = survivors.map { it.value }
    // The index tells repeated occurrences of a track apart; the key alone still restores a state
    // saved without it.
    val current =
        saved.currentIndex.takeIf { saved.queue.getOrNull(it) == saved.currentKey }
            ?: saved.queue.indexOf(saved.currentKey)
    val kept = survivors.indexOfFirst { it.index == current }
    if (kept >= 0) return RestoredQueue(keys, kept, saved.positionMs)
    val next = survivors.indexOfFirst { it.index > current }
    return RestoredQueue(keys, if (next >= 0) next else survivors.lastIndex, 0)
}
