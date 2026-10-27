package com.oruponu.segue.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
abstract class AnalysisDao {
    @Query("SELECT * FROM analysis WHERE track_key = :key")
    abstract suspend fun analysis(key: String): AnalysisEntity?

    @Query("SELECT * FROM track_styles WHERE track_key = :key ORDER BY rank")
    abstract suspend fun styles(key: String): List<TrackStyleEntity>

    @Transaction
    open suspend fun save(analysis: AnalysisEntity, styles: List<TrackStyleEntity>) {
        deleteStyles(listOf(analysis.trackKey))
        upsertAnalysis(analysis)
        insertStyles(styles)
    }

    @Transaction
    open suspend fun deleteFor(keys: List<String>) {
        deleteStyles(keys)
        deleteAnalysis(keys)
    }

    @Upsert protected abstract suspend fun upsertAnalysis(analysis: AnalysisEntity)

    @Insert protected abstract suspend fun insertStyles(styles: List<TrackStyleEntity>)

    @Query("DELETE FROM track_styles WHERE track_key IN (:keys)")
    protected abstract suspend fun deleteStyles(keys: List<String>)

    @Query("DELETE FROM analysis WHERE track_key IN (:keys)")
    protected abstract suspend fun deleteAnalysis(keys: List<String>)
}
