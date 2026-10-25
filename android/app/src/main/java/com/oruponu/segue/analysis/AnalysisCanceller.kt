package com.oruponu.segue.analysis

import javax.inject.Inject

interface AnalysisCanceller {
    // Called after the library sync has updated or deleted these tracks, outside the lock.
    fun onTracksChanged(keys: Collection<String>)
}

class NoOpAnalysisCanceller @Inject constructor() : AnalysisCanceller {
    override fun onTracksChanged(keys: Collection<String>) = Unit
}
