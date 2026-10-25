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
