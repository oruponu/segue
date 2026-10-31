package com.oruponu.segue.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.contentUri

/**
 * The system media controls show a missing artist as "null", so [unknownArtist] takes its place.
 */
fun TrackEntity.toMediaItem(unknownArtist: String): MediaItem {
    val uri = contentUri()
    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist ?: unknownArtist)
                .setAlbumTitle(album)
                .setArtworkUri(uri)
                .build(),
        )
        .build()
}
