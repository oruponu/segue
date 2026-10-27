package com.oruponu.segue.library

import android.content.ContentResolver
import android.content.ContentUris
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.oruponu.segue.data.db.TrackEntity

private const val JPEG_QUALITY = 90
private const val MIME_TYPE = "image/jpeg"

data class ArtworkRequest(
    val trackKey: String,
    val uri: Uri,
    val size: Long,
    val dateModified: Long,
    val px: Int,
)

fun TrackEntity.contentUri(): Uri =
    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)

fun TrackEntity.artworkRequest(px: Int): ArtworkRequest =
    ArtworkRequest(key, contentUri(), size, dateModified, px)

private fun ArtworkRequest.cacheKey(): String = artworkCacheKey(trackKey, size, dateModified, px)

class ArtworkKeyer : Keyer<ArtworkRequest> {
    override fun key(data: ArtworkRequest, options: Options): String = data.cacheKey()
}

class ArtworkFetcher(
    private val data: ArtworkRequest,
    private val options: Options,
    private val imageLoader: ImageLoader,
    private val contentResolver: ContentResolver,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val diskCacheKey = data.cacheKey()
        val diskCache = imageLoader.diskCache

        if (diskCache != null && options.diskCachePolicy.readEnabled) {
            diskCache.openSnapshot(diskCacheKey)?.let {
                return it.toResult(diskCache, diskCacheKey, DataSource.DISK)
            }
        }

        // IOException (no embedded art) propagates; Coil turns it into an ErrorResult.
        val bitmap = contentResolver.loadThumbnail(data.uri, Size(data.px, data.px), null)

        val snapshot =
            diskCache
                ?.takeIf { options.diskCachePolicy.writeEnabled }
                ?.let { writeToCache(it, diskCacheKey, bitmap) }
        // The thumbnail was just decoded in memory; DISK is reserved for cache hits.
        if (snapshot != null) {
            bitmap.recycle()
            return snapshot.toResult(diskCache, diskCacheKey, DataSource.MEMORY)
        }
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.MEMORY)
    }

    private fun writeToCache(
        diskCache: DiskCache,
        diskCacheKey: String,
        bitmap: Bitmap,
    ): DiskCache.Snapshot? {
        val editor = diskCache.openEditor(diskCacheKey) ?: return null
        try {
            diskCache.fileSystem.write(editor.data) {
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream())) {
                    "Failed to encode artwork thumbnail"
                }
            }
            return editor.commitAndOpenSnapshot()
        } catch (e: Exception) {
            editor.abort()
            bitmap.recycle()
            throw e
        }
    }

    private fun DiskCache.Snapshot.toResult(
        diskCache: DiskCache,
        diskCacheKey: String,
        dataSource: DataSource,
    ): SourceFetchResult =
        SourceFetchResult(
            source = ImageSource(data, diskCache.fileSystem, diskCacheKey, this),
            mimeType = MIME_TYPE,
            dataSource = dataSource,
        )

    class Factory(private val contentResolver: ContentResolver) : Fetcher.Factory<ArtworkRequest> {
        override fun create(
            data: ArtworkRequest,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = ArtworkFetcher(data, options, imageLoader, contentResolver)
    }
}
