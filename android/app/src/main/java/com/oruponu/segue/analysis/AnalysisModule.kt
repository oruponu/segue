package com.oruponu.segue.analysis

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {
    @Binds abstract fun bindEngine(engine: SegueCoreEngine): AnalysisEngine

    companion object {
        @Provides
        @Singleton
        fun provideVisualsCache(
            @ApplicationContext context: Context,
            engine: AnalysisEngine,
        ): VisualsCache {
            val dir = context.cacheDir.resolve("visuals").also { it.mkdirs() }
            return VisualsCache(dir, engine).also { it.removeStaleTemps() }
        }
    }
}
