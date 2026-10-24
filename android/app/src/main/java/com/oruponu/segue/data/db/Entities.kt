package com.oruponu.segue.data.db

data class TrackEntity(
    val key: String,
    val mediaStoreId: Long,
    val relativePath: String,
    val displayName: String,
    val title: String,
    val album: String,
    val artist: String?,
    val albumArtist: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val durationMs: Long,
    val size: Long,
    val dateModified: Long,
    val albumKey: String,
)
