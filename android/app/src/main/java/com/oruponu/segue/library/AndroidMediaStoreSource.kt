package com.oruponu.segue.library

import android.content.Context
import android.database.ContentObserver
import android.provider.MediaStore.Audio.Media
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.oruponu.segue.data.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TreeSet
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

private val PROJECTION =
    arrayOf(
        Media._ID,
        Media.DISPLAY_NAME,
        Media.RELATIVE_PATH,
        Media.TITLE,
        Media.ALBUM,
        Media.ARTIST,
        Media.ALBUM_ARTIST,
        Media.CD_TRACK_NUMBER,
        Media.DISC_NUMBER,
        Media.DURATION,
        Media.SIZE,
        Media.DATE_MODIFIED,
    )

class AndroidMediaStoreSource
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : MediaStoreSource {
    private val uri = Media.EXTERNAL_CONTENT_URI

    override suspend fun folders(): List<String> =
        withContext(ioDispatcher) {
            val folders = TreeSet<String>()
            context.contentResolver
                .query(uri, arrayOf(Media.RELATIVE_PATH), "is_music=1", null, null)
                ?.use { cursor ->
                    val column = cursor.getColumnIndexOrThrow(Media.RELATIVE_PATH)
                    while (cursor.moveToNext()) cursor.getStringOrNull(column)?.let(folders::add)
                }
            folders.toList()
        }

    override suspend fun query(folder: String): List<MediaStoreTrack> =
        withContext(ioDispatcher) {
            val (selection, args) = musicSelection(folder)
            // A null cursor means MediaStore is unavailable; an empty result would wipe the
            // library.
            val cursor =
                checkNotNull(
                    context.contentResolver.query(
                        uri,
                        PROJECTION,
                        selection,
                        args,
                        "${Media._ID} ASC",
                    ),
                ) {
                    "MediaStore returned no cursor"
                }
            cursor.use { c ->
                val id = c.getColumnIndexOrThrow(Media._ID)
                val displayName = c.getColumnIndexOrThrow(Media.DISPLAY_NAME)
                val relativePath = c.getColumnIndexOrThrow(Media.RELATIVE_PATH)
                val title = c.getColumnIndexOrThrow(Media.TITLE)
                val album = c.getColumnIndexOrThrow(Media.ALBUM)
                val artist = c.getColumnIndexOrThrow(Media.ARTIST)
                val albumArtist = c.getColumnIndexOrThrow(Media.ALBUM_ARTIST)
                val cdTrackNumber = c.getColumnIndexOrThrow(Media.CD_TRACK_NUMBER)
                val discNumber = c.getColumnIndexOrThrow(Media.DISC_NUMBER)
                val duration = c.getColumnIndexOrThrow(Media.DURATION)
                val size = c.getColumnIndexOrThrow(Media.SIZE)
                val dateModified = c.getColumnIndexOrThrow(Media.DATE_MODIFIED)
                buildList {
                    while (c.moveToNext()) {
                        add(
                            MediaStoreTrack(
                                id = c.getLong(id),
                                displayName = c.getStringOrNull(displayName) ?: continue,
                                relativePath = c.getStringOrNull(relativePath) ?: continue,
                                title = c.getStringOrNull(title),
                                album = c.getStringOrNull(album),
                                artist = c.getStringOrNull(artist),
                                albumArtist = c.getStringOrNull(albumArtist),
                                cdTrackNumber = c.getStringOrNull(cdTrackNumber),
                                discNumber = c.getStringOrNull(discNumber),
                                durationMs = c.getLongOrNull(duration),
                                size = c.getLong(size),
                                dateModified = c.getLong(dateModified),
                            ),
                        )
                    }
                }
            }
        }

    override fun changes(): Flow<Unit> = callbackFlow {
        val observer =
            object : ContentObserver(null) {
                override fun onChange(selfChange: Boolean) {
                    trySend(Unit)
                }
            }
        context.contentResolver.registerContentObserver(uri, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
