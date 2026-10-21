package com.oruponu.segue.core

/**
 * Loaded style classification model (SegueModels). Reuse it within the process and release it with
 * close().
 */
class NativeModels internal constructor(private var handle: Long) : AutoCloseable {
    internal fun requireHandle(): Long {
        check(handle != 0L) { "NativeModels is closed" }
        return handle
    }

    override fun close() {
        if (handle != 0L) {
            NativeLib.nativeModelsFree(handle)
            handle = 0L
        }
    }
}
