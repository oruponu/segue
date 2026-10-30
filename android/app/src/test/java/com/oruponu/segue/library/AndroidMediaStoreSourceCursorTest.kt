package com.oruponu.segue.library

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidMediaStoreSourceCursorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val source = AndroidMediaStoreSource(context, Dispatchers.Unconfined)

    @Before
    fun registerNullCursorProvider() {
        Robolectric.setupContentProvider(NullCursorProvider::class.java, MediaStore.AUTHORITY)
    }

    @Test
    fun foldersWithoutCursorThrows() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertThrows(IllegalStateException::class.java) { runBlocking { source.folders() } }
    }
}

/** Mimics an unavailable MediaStore. */
class NullCursorProvider : ContentProvider() {
    override fun onCreate() = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
