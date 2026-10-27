package com.oruponu.segue.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun statusToString(status: AnalysisStatus): String = status.name

    @TypeConverter fun stringToStatus(value: String): AnalysisStatus = AnalysisStatus.valueOf(value)
}

@Database(
    entities = [TrackEntity::class, AnalysisEntity::class, TrackStyleEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SegueDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    abstract fun analysisDao(): AnalysisDao
}
