package com.oruponu.segue.data

import android.content.Context
import androidx.room.Room
import com.oruponu.segue.data.db.AnalysisDao
import com.oruponu.segue.data.db.SegueDatabase
import com.oruponu.segue.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executors
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Single thread so that audio analysis never runs concurrently.
    @Provides
    @Singleton
    @AnalysisDispatcher
    fun provideAnalysisDispatcher(): CoroutineDispatcher =
        Executors.newSingleThreadExecutor { Thread(it, "segue-analysis") }.asCoroutineDispatcher()

    @Provides @IoDispatcher fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SegueDatabase =
        Room.databaseBuilder(context, SegueDatabase::class.java, "segue.db").build()

    @Provides fun provideTrackDao(db: SegueDatabase): TrackDao = db.trackDao()

    @Provides fun provideAnalysisDao(db: SegueDatabase): AnalysisDao = db.analysisDao()
}
