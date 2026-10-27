package com.oruponu.segue.playback

import com.oruponu.segue.analysis.AnalysisRepository
import com.oruponu.segue.analysis.VisualsCache
import com.oruponu.segue.data.db.TrackDao
import com.oruponu.segue.library.LibraryRepository
import com.oruponu.segue.library.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// For instrumented tests: Hilt adds entry points to the app component only when they are compiled
// with the app, so this cannot live in androidTest.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    fun trackDao(): TrackDao

    fun playbackStateStore(): PlaybackStateStore

    fun playbackConnection(): PlaybackConnection

    fun libraryRepository(): LibraryRepository

    fun settingsStore(): SettingsStore

    fun analysisRepository(): AnalysisRepository

    fun visualsCache(): VisualsCache
}
