package com.oruponu.segue.analysis

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "ModelProvider"

@Singleton
class ModelProvider
@Inject
constructor(
    private val store: ModelStore,
    private val engine: AnalysisEngine,
) {
    private val mutex = Mutex()
    private var loaded = false
    private var models: EngineModels? = null

    @Volatile
    var unavailable: Boolean = false
        private set

    // A failure is kept too: the models are not retried within the process.
    suspend fun models(): EngineModels? = mutex.withLock {
        if (!loaded) {
            models = load()
            unavailable = models == null
            loaded = true
        }
        models
    }

    private fun load(): EngineModels? =
        try {
            val file = checkNotNull(store.prepare()) { "Cannot prepare the style model" }
            engine.loadModels(file.path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Cannot load the style model; analyzing without styles", e)
            null
        }
}
