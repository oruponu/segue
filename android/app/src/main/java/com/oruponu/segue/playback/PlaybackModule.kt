package com.oruponu.segue.playback

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.oruponu.segue.data.JsonSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    @Provides
    @Singleton
    fun providePlaybackStateStore(@ApplicationContext context: Context): PlaybackStateStore =
        PlaybackStateStore(
            DataStoreFactory.create(
                serializer = JsonSerializer(SavedPlayback.serializer(), SavedPlayback()),
                corruptionHandler = ReplaceFileCorruptionHandler { SavedPlayback() },
            ) {
                context.filesDir.resolve("datastore/playback.json")
            },
        )
}
