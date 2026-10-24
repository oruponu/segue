package com.oruponu.segue.core

/** Decoded audio (SegueAudio). Use it from a single thread and release it with close(). */
class NativeAudio internal constructor(private var handle: Long) : AutoCloseable {
    internal fun requireHandle(): Long {
        check(handle != 0L) { "NativeAudio is closed" }
        return handle
    }

    override fun close() {
        if (handle != 0L) {
            NativeLib.nativeAudioFree(handle)
            handle = 0L
        }
    }
}
