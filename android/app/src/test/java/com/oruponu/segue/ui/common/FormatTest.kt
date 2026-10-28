package com.oruponu.segue.ui.common

import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59_999))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("10:00", formatDuration(600_000))
    }

    @Test
    fun formatsHours() {
        assertEquals("1:00:00", formatDuration(3_600_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
    }

    @Test
    fun albumDurationSumsTracks() {
        val album = Album("a", "Album", null, listOf(track("1", 1000), track("2", 2500)))
        assertEquals(3500L, album.durationMs())
    }

    private fun track(key: String, durationMs: Long) =
        TrackEntity(
            key = key,
            mediaStoreId = 1,
            relativePath = "Music/A/",
            displayName = "$key.mp3",
            title = key,
            album = "Album",
            artist = null,
            albumArtist = null,
            discNumber = null,
            trackNumber = null,
            durationMs = durationMs,
            size = 1,
            dateModified = 1,
            albumKey = "a",
        )
}
