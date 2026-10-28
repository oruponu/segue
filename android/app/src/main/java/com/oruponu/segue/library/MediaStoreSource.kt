package com.oruponu.segue.library

import kotlinx.coroutines.flow.Flow

interface MediaStoreSource {
    // Distinct RELATIVE_PATH values of music files, in natural string order. Throws
    // SecurityException without permission and IllegalStateException when MediaStore returns no
    // cursor.
    suspend fun folders(): List<String>

    // Music files under the folder ordered by _ID. Throws SecurityException without permission and
    // IllegalStateException when MediaStore returns no cursor.
    suspend fun query(folder: String): List<MediaStoreTrack>

    fun changes(): Flow<Unit>
}
