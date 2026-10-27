package com.oruponu.segue.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks") fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks") suspend fun all(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE `key` = :key") suspend fun byKey(key: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE `key` IN (:keys)")
    suspend fun byKeys(keys: List<String>): List<TrackEntity>

    @Upsert suspend fun upsert(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks WHERE `key` IN (:keys)") suspend fun delete(keys: List<String>)
}

// Each key is one bound parameter. A key list such as a saved queue can exceed the 32766 that
// SQLite allows per statement; 999 stays well under it, leaving room for other parameters.
private const val KEY_CHUNK_SIZE = 999

suspend fun TrackDao.byKeysChunked(keys: List<String>): List<TrackEntity> =
    keys.chunked(KEY_CHUNK_SIZE).flatMap { byKeys(it) }
