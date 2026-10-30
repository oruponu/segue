package com.oruponu.segue.playback

import com.oruponu.segue.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaItemsTest {
    private fun track(artist: String?) =
        TrackEntity(
            key = "a",
            mediaStoreId = 1,
            relativePath = "Music/A/",
            displayName = "a.mp3",
            title = "Title",
            album = "Album",
            artist = artist,
            albumArtist = null,
            discNumber = null,
            trackNumber = null,
            durationMs = 1000,
            size = 1,
            dateModified = 1,
            albumKey = "album",
        )

    @Test
    fun artistIsKept() {
        val item = track(artist = "Artist").toMediaItem(unknownArtist = "Unknown")

        assertEquals("Artist", item.mediaMetadata.artist)
    }

    @Test
    fun missingArtistUsesUnknownArtist() {
        val item = track(artist = null).toMediaItem(unknownArtist = "Unknown")

        assertEquals("Unknown", item.mediaMetadata.artist)
    }
}
