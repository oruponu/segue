package com.oruponu.segue.analysis

interface AnalysisCanceller {
    // Called after the library sync has updated or deleted these tracks, outside the lock.
    fun onTracksChanged(keys: Collection<String>)
}
