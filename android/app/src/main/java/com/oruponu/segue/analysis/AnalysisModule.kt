package com.oruponu.segue.analysis

import android.content.Context
import android.util.Log
import com.oruponu.segue.data.ApplicationScope
import com.oruponu.segue.playback.PlaybackConnection
import com.oruponu.segue.ui.analysis.StyleLabels
import com.oruponu.segue.ui.analysis.loadStyleLabels
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map

private const val MODEL_NAME = "discogs-effnet-bsdynamic-1.onnx"

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {
    @Binds abstract fun bindEngine(engine: SegueCoreEngine): AnalysisEngine

    @Binds abstract fun bindTrackFileOpener(opener: AndroidTrackFileOpener): TrackFileOpener

    @Binds abstract fun bindAnalysisCanceller(repository: AnalysisRepository): AnalysisCanceller

    companion object {
        @Provides
        @Singleton
        fun provideAnalysisCoordinator(
            connection: PlaybackConnection,
            repository: AnalysisRepository,
            @ApplicationScope scope: CoroutineScope,
        ): AnalysisCoordinator =
            AnalysisCoordinator(
                connection.state.map { it.currentKey },
                repository::setCurrent,
                scope,
            )

        @Provides
        @Singleton
        fun provideVisualsCache(
            @ApplicationContext context: Context,
            engine: AnalysisEngine,
        ): VisualsCache {
            val dir = context.cacheDir.resolve("visuals").also { it.mkdirs() }
            return VisualsCache(dir, engine).also { it.removeStaleTemps() }
        }

        @Provides
        @Singleton
        fun provideModelStore(@ApplicationContext context: Context): ModelStore {
            val asset =
                object : ModelAsset {
                    override val length: Long
                        get() = context.assets.openFd(MODEL_NAME).use { it.length }

                    override fun open(): InputStream = context.assets.open(MODEL_NAME)
                }
            return ModelStore(asset, context.noBackupFilesDir.resolve("models/$MODEL_NAME")) {
                Log.w("ModelStore", "Cannot prepare the model", it)
            }
        }

        @Provides
        @Singleton
        fun provideStyleLabels(@ApplicationContext context: Context): StyleLabels =
            loadStyleLabels(context.assets)
    }
}
