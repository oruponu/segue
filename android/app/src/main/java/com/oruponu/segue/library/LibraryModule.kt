package com.oruponu.segue.library

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.oruponu.segue.data.JsonSerializer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds abstract fun bindMediaStoreSource(source: AndroidMediaStoreSource): MediaStoreSource

    companion object {
        @Provides
        @Singleton
        fun provideSettingsStore(@ApplicationContext context: Context): SettingsStore =
            SettingsStore(
                DataStoreFactory.create(
                    serializer = JsonSerializer(Settings.serializer(), Settings()),
                    corruptionHandler = ReplaceFileCorruptionHandler { Settings() },
                ) {
                    context.filesDir.resolve("datastore/settings.json")
                },
            )
    }
}
