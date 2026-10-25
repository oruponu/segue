package com.oruponu.segue.analysis

import android.content.Context
import android.util.Log
import com.oruponu.segue.core.SegueException
import com.oruponu.segue.core.SegueStatus
import com.oruponu.segue.data.db.TrackEntity
import com.oruponu.segue.library.contentUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.Closeable
import java.io.FileNotFoundException
import javax.inject.Inject

private const val TAG = "TrackFileOpener"

class OpenedTrack(val path: String, private val closeable: Closeable) : Closeable {
    override fun close() = closeable.close()
}

interface TrackFileOpener {
    /** Throws [SegueException] with [SegueStatus.IO_FAILED] when the file cannot be opened. */
    fun open(track: TrackEntity): OpenedTrack
}

class AndroidTrackFileOpener
@Inject
constructor(@param:ApplicationContext private val context: Context) : TrackFileOpener {
    override fun open(track: TrackEntity): OpenedTrack {
        val pfd =
            try {
                context.contentResolver.openFileDescriptor(track.contentUri(), "r")
            } catch (e: FileNotFoundException) {
                Log.w(TAG, "Cannot open ${track.key}", e)
                null
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot open ${track.key}", e)
                null
            } ?: throw SegueException(SegueStatus.IO_FAILED)
        return OpenedTrack("/proc/self/fd/${pfd.fd}", pfd)
    }
}
