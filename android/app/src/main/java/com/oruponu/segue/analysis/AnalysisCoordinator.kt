package com.oruponu.segue.analysis

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Scoped as a singleton by its provider.
class AnalysisCoordinator(
    private val currentKeys: Flow<String?>,
    private val setCurrent: (String?) -> Unit,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { currentKeys.distinctUntilChanged().collect { setCurrent(it) } }
    }
}
