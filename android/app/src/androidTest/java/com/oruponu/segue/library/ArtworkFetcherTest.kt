package com.oruponu.segue.library

import android.Manifest
import android.util.Size
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.oruponu.segue.data.db.TrackEntity
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

private const val PX = 256

class ArtworkFetcherTest {
    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AndroidMediaStoreSource(context, Dispatchers.IO)
    private val cacheDir = File(context.cacheDir, "artwork-test-${UUID.randomUUID()}")
    private val imageLoader =
        ImageLoader.Builder(context)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCache { DiskCache.Builder().directory(cacheDir).build() }
            .components {
                add(ArtworkKeyer())
                add(ArtworkFetcher.Factory(context.contentResolver))
            }
            .build()

    @After
    fun tearDown() {
        imageLoader.shutdown()
        cacheDir.deleteRecursively()
    }

    @Test
    fun secondLoadComesFromDiskCache() = runBlocking {
        val track = findTrack(withArt = true)
        assumeTrue("No track with artwork on the device", track != null)

        val first = imageLoader.execute(request(track!!))
        assertTrue(first.toString(), first is SuccessResult)
        assertEquals(DataSource.MEMORY, (first as SuccessResult).dataSource)

        val second = imageLoader.execute(request(track))
        assertTrue(second.toString(), second is SuccessResult)
        assertEquals(DataSource.DISK, (second as SuccessResult).dataSource)
    }

    @Test
    fun trackWithoutArtFails() = runBlocking {
        val track = findTrack(withArt = false)
        assumeTrue("No track without artwork on the device", track != null)

        val result = imageLoader.execute(request(track!!))
        assertTrue(result.toString(), result is ErrorResult)
        assertTrue((result as ErrorResult).throwable.toString(), result.throwable is IOException)
    }

    private fun request(track: TrackEntity) =
        ImageRequest.Builder(context).data(track.artworkRequest(PX)).build()

    private suspend fun findTrack(withArt: Boolean): TrackEntity? =
        source
            .folders()
            .flatMap { source.query(it) }
            .map { it.toEntity() }
            .distinctBy { it.key }
            .firstOrNull { hasThumbnail(it) == withArt }

    private fun hasThumbnail(track: TrackEntity): Boolean =
        try {
            context.contentResolver.loadThumbnail(track.contentUri(), Size(PX, PX), null).recycle()
            true
        } catch (_: IOException) {
            false
        }
}
