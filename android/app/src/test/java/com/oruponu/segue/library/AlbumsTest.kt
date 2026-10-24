package com.oruponu.segue.library

import com.oruponu.segue.data.db.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumsTest {
    private fun track(
        key: String,
        album: String? = "Album",
        albumArtist: String? = null,
        artist: String? = null,
        path: String = "Music/A/",
        disc: Int? = null,
        number: Int? = null,
    ): TrackEntity {
        val albumName = albumDisplayName(album)
        return TrackEntity(
            key = key,
            mediaStoreId = 1,
            relativePath = path,
            displayName = "$key.mp3",
            title = key,
            album = albumName,
            artist = artist,
            albumArtist = albumArtist,
            discNumber = disc,
            trackNumber = number,
            durationMs = 1000,
            size = 1,
            dateModified = 1,
            albumKey = albumKeyOf(album, albumArtist, path),
        )
    }

    @Test
    fun albumArtistGroupsAcrossFolders() {
        val albums =
            groupAlbums(
                listOf(
                    track("a", albumArtist = "X", path = "Music/A/"),
                    track("b", albumArtist = "X", path = "Music/B/"),
                ),
            )
        assertEquals(1, albums.size)
        assertEquals(listOf("a", "b"), albums[0].tracks.map { it.key })
    }

    @Test
    fun noAlbumArtistSplitsByFolder() {
        val albums =
            groupAlbums(listOf(track("a", path = "Music/A/"), track("b", path = "Music/B/")))
        assertEquals(2, albums.size)
    }

    @Test
    fun missingAlbumIsUnknownAlbum() {
        val albums =
            groupAlbums(listOf(track("a", album = null), track("b", album = ""), track("c")))
        assertEquals(listOf("Album", UNKNOWN_ALBUM), albums.map { it.title })
    }

    @Test
    fun albumsSortByTitleWithUnknownLast() {
        val albums =
            groupAlbums(
                listOf(
                    track("1", album = "B"),
                    track("2", album = null),
                    track("3", album = "A"),
                    track("4", album = "Zebra"),
                ),
            )
        assertEquals(listOf("A", "B", "Zebra", UNKNOWN_ALBUM), albums.map { it.title })
    }

    @Test
    fun tracksSortByDiscThenTrackThenKey() {
        val albums =
            groupAlbums(
                listOf(
                    track("t21", disc = 2, number = 1),
                    track("t12", disc = 1, number = 2),
                    track("t1n", disc = 1, number = null),
                    track("b"),
                    track("a"),
                ),
            )
        assertEquals(listOf("a", "b", "t1n", "t12", "t21"), albums.single().tracks.map { it.key })
    }

    @Test
    fun albumArtistFallsBackToFirstTrackArtist() {
        val withAlbumArtist =
            groupAlbums(listOf(track("a", albumArtist = "AA", artist = "P"))).single()
        assertEquals("AA", withAlbumArtist.artist)

        val fallback =
            groupAlbums(
                    listOf(
                        track("b", artist = "Second", number = 2),
                        track("a", artist = "First", number = 1),
                    ),
                )
                .single()
        assertEquals("First", fallback.artist)
    }
}
