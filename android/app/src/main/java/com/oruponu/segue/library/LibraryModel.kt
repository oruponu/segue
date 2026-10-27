package com.oruponu.segue.library

import android.provider.MediaStore
import com.oruponu.segue.data.db.TrackEntity

const val UNKNOWN_ALBUM = "Unknown Album"

data class MediaStoreTrack(
    val id: Long,
    val displayName: String,
    val relativePath: String,
    val title: String?,
    val album: String?,
    val artist: String?,
    val albumArtist: String?,
    val cdTrackNumber: String?,
    val discNumber: String?,
    val durationMs: Long?,
    val size: Long,
    val dateModified: Long,
)

// RELATIVE_PATH already ends with "/".
fun trackKey(relativePath: String, displayName: String): String = relativePath + displayName

// MediaStore returns track and disc numbers verbatim, e.g. "3/12".
fun parseLeadingNumber(value: String?): Int? =
    value?.trimStart()?.takeWhile { it in '0'..'9' }?.toIntOrNull()

fun displayTitle(title: String?, displayName: String): String =
    if (title.isNullOrBlank()) displayName.substringBeforeLast('.') else title

fun albumDisplayName(album: String?): String = if (album.isNullOrBlank()) UNKNOWN_ALBUM else album

fun albumKeyOf(album: String?, albumArtist: String?, relativePath: String): String {
    val name = albumDisplayName(album)
    return if (albumArtist.isNullOrBlank()) "path\u0000$relativePath\u0000$name"
    else "artist\u0000$albumArtist\u0000$name"
}

// MediaStore fills some missing tags with "<unknown>", e.g. the artist of an untagged file.
fun knownTag(value: String?): String? = value?.takeUnless { it == MediaStore.UNKNOWN_STRING }

fun MediaStoreTrack.toEntity(): TrackEntity {
    val album = knownTag(this.album)
    val albumArtist = knownTag(this.albumArtist)
    return TrackEntity(
        key = trackKey(relativePath, displayName),
        mediaStoreId = id,
        relativePath = relativePath,
        displayName = displayName,
        title = displayTitle(title, displayName),
        album = albumDisplayName(album),
        artist = knownTag(artist),
        albumArtist = albumArtist,
        discNumber = parseLeadingNumber(discNumber),
        trackNumber = parseLeadingNumber(cdTrackNumber),
        durationMs = durationMs ?: 0,
        size = size,
        dateModified = dateModified,
        albumKey = albumKeyOf(album, albumArtist, relativePath),
    )
}

fun musicSelection(folder: String): Pair<String, Array<String>> {
    val escaped = folder.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "is_music=1 AND relative_path LIKE ? ESCAPE '\\'" to arrayOf("$escaped%")
}

fun artworkCacheKey(trackKey: String, size: Long, dateModified: Long, px: Int): String =
    "art:$trackKey:$size:$dateModified:$px"
