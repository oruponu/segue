package com.oruponu.segue.playback

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture

private val THUMBNAIL_SIZE = Size(512, 512)

/** Loads embedded artwork of MediaStore audio, which the default loader cannot decode. */
@OptIn(UnstableApi::class)
class ThumbnailBitmapLoader(context: Context) : BitmapLoader {
    private val contentResolver = context.contentResolver
    private val fallback = DataSourceBitmapLoader.Builder(context).build()
    private val executor = DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get()

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority == MediaStore.AUTHORITY) {
            executor.submit<Bitmap> { contentResolver.loadThumbnail(uri, THUMBNAIL_SIZE, null) }
        } else {
            fallback.loadBitmap(uri)
        }
}
