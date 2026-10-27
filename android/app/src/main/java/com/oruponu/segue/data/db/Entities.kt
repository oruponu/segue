package com.oruponu.segue.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "media_store_id") val mediaStoreId: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    val title: String,
    val album: String,
    val artist: String?,
    @ColumnInfo(name = "album_artist") val albumArtist: String?,
    @ColumnInfo(name = "disc_number") val discNumber: Int?,
    @ColumnInfo(name = "track_number") val trackNumber: Int?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val size: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "album_key") val albumKey: String,
)

enum class AnalysisStatus {
    OK,
    DECODE_FAILED,
    ANALYSIS_FAILED,
}

@Entity(
    tableName = "analysis",
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["key"],
                childColumns = ["track_key"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class AnalysisEntity(
    @PrimaryKey @ColumnInfo(name = "track_key") val trackKey: String,
    @ColumnInfo(name = "analysis_version") val analysisVersion: Int,
    val status: AnalysisStatus,
    @ColumnInfo(name = "styles_done") val stylesDone: Boolean,
    val bpm: Float?,
    @ColumnInfo(name = "bpm_confidence") val bpmConfidence: Float?,
    @ColumnInfo(name = "key_note") val keyNote: Int?,
    @ColumnInfo(name = "key_scale") val keyScale: Int?,
    @ColumnInfo(name = "key_confidence") val keyConfidence: Float?,
    @ColumnInfo(name = "analyzed_at") val analyzedAt: Long,
)

@Entity(
    tableName = "track_styles",
    primaryKeys = ["track_key", "rank"],
    foreignKeys =
        [
            ForeignKey(
                entity = TrackEntity::class,
                parentColumns = ["key"],
                childColumns = ["track_key"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class TrackStyleEntity(
    @ColumnInfo(name = "track_key") val trackKey: String,
    val rank: Int,
    @ColumnInfo(name = "label_index") val labelIndex: Int,
    val confidence: Float,
)
