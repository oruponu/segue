package com.oruponu.segue.core

object SegueCore {
    /** Initializes Essentia. Safe to call any number of times (each function also initializes if needed). */
    fun init() = checkStatus(NativeLib.nativeInit())

    /** Version of the analysis and visuals output. Used to invalidate the DB and caches. */
    val analysisVersion: Int get() = NativeLib.nativeAnalysisVersion()
}
