package com.oruponu.segue.library

import com.oruponu.segue.data.db.TrackEntity

data class Album(
    val key: String,
    val title: String,
    val artist: String?,
    val tracks: List<TrackEntity>,
)

private val trackOrder =
    compareBy<TrackEntity>({ it.discNumber ?: 0 }, { it.trackNumber ?: 0 }, { it.key })

fun groupAlbums(tracks: List<TrackEntity>): List<Album> =
    tracks
        .groupBy { it.albumKey }
        .map { (key, group) ->
            val sorted = group.sortedWith(trackOrder)
            Album(
                key = key,
                title = sorted.first().album,
                artist =
                    sorted.firstNotNullOfOrNull { it.albumArtist?.takeIf(String::isNotBlank) }
                        ?: sorted.first().artist,
                tracks = sorted,
            )
        }
        .sortedWith(
            compareBy<Album>({ it.title == UNKNOWN_ALBUM }, { it.title }, { it.key }),
        )
