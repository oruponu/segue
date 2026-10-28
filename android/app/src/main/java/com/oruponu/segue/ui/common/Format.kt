package com.oruponu.segue.ui.common

import com.oruponu.segue.library.Album

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = totalSeconds % 3600 / 60
    val seconds = (totalSeconds % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$seconds"
    else "$minutes:$seconds"
}

fun Album.durationMs(): Long = tracks.sumOf { it.durationMs }
