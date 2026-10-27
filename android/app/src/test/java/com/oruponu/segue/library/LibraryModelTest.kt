package com.oruponu.segue.library

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryModelTest {
    @Test
    fun parsesLeadingNumbers() {
        assertEquals(3, parseLeadingNumber("3/12"))
        assertEquals(7, parseLeadingNumber(" 07"))
        assertNull(parseLeadingNumber("A1"))
        assertNull(parseLeadingNumber(""))
        assertNull(parseLeadingNumber(null))
    }

    @Test
    fun titleFallsBackToFileName() {
        assertEquals("01 Intro", displayTitle("  ", "01 Intro.flac"))
        assertEquals("Song", displayTitle("Song", "x.mp3"))
        assertEquals("noext", displayTitle(null, "noext"))
    }

    @Test
    fun selectionEscapesLikeWildcards() {
        val (where, args) = musicSelection("Music/100%_Hits/")
        assertEquals("is_music=1 AND relative_path LIKE ? ESCAPE '\\'", where)
        assertArrayEquals(arrayOf("Music/100\\%\\_Hits/%"), args)
    }

    @Test
    fun artworkKeyChangesWithSizeOrDate() {
        val k = artworkCacheKey("Music/a.mp3", 100, 200, 96)
        assertEquals("art:Music/a.mp3:100:200:96", k)
        assertNotEquals(k, artworkCacheKey("Music/a.mp3", 101, 200, 96))
        assertNotEquals(k, artworkCacheKey("Music/a.mp3", 100, 201, 96))
    }

    @Test
    fun albumKeyFormats() {
        assertEquals(
            "artist\u0000Artist\u0000Album",
            albumKeyOf("Album", "Artist", "Music/X/"),
        )
        assertEquals("path\u0000Music/X/\u0000Album", albumKeyOf("Album", " ", "Music/X/"))
        assertEquals("path\u0000Music/X/\u0000Album", albumKeyOf("Album", null, "Music/X/"))
        assertEquals(
            "path\u0000Music/X/\u0000$UNKNOWN_ALBUM",
            albumKeyOf(null, null, "Music/X/"),
        )
    }

    @Test
    fun toEntityMapsFields() {
        val entity =
            MediaStoreTrack(
                    id = 42,
                    displayName = "01 Intro.flac",
                    relativePath = "Music/X/",
                    title = " ",
                    album = "Album",
                    artist = "Artist",
                    albumArtist = null,
                    cdTrackNumber = "1/3",
                    discNumber = "2/2",
                    durationMs = null,
                    size = 10,
                    dateModified = 20,
                )
                .toEntity()
        assertEquals("Music/X/01 Intro.flac", entity.key)
        assertEquals(42L, entity.mediaStoreId)
        assertEquals("01 Intro", entity.title)
        assertEquals("Album", entity.album)
        assertEquals(1, entity.trackNumber)
        assertEquals(2, entity.discNumber)
        assertEquals(0L, entity.durationMs)
        assertEquals("path\u0000Music/X/\u0000Album", entity.albumKey)
    }

    @Test
    fun toEntityTreatsUnknownTagsAsMissing() {
        val entity =
            MediaStoreTrack(
                    id = 1,
                    displayName = "loose.m4a",
                    relativePath = "Music/X/",
                    title = null,
                    album = "<unknown>",
                    artist = "<unknown>",
                    albumArtist = "<unknown>",
                    cdTrackNumber = null,
                    discNumber = null,
                    durationMs = null,
                    size = 10,
                    dateModified = 20,
                )
                .toEntity()
        assertEquals(UNKNOWN_ALBUM, entity.album)
        assertNull(entity.artist)
        assertNull(entity.albumArtist)
        assertEquals("path\u0000Music/X/\u0000$UNKNOWN_ALBUM", entity.albumKey)
    }
}
