package com.oruponu.segue.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.contentUri

fun TrackEntity.toMediaItem(): MediaItem {
    val uri = contentUri()
    return MediaItem.Builder()
        .setMediaId(key)
        .setUri(uri)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(uri)
                .build(),
        )
        .build()
}

/**
 * Items sent by a MediaController arrive without localConfiguration; the URI travels in the request
 * metadata.
 */
fun MediaItem.withResolvedUri(): MediaItem {
    if (localConfiguration != null) return this
    val uri = requestMetadata.mediaUri ?: return this
    return buildUpon().setUri(uri).build()
}
