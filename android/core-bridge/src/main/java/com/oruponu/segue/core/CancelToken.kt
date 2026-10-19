package com.oruponu.segue.core

/**
 * Cancel token for native operations.
 * request() may be called from another thread. Call close() only after the native calls that
 * received this token have returned.
 */
class CancelToken : AutoCloseable {
    private val lock = Any()
    private var handle: Long = NativeLib.nativeCancelCreate().also {
        check(it != 0L) { "segue_cancel_create failed" }
    }

    fun request() {
        synchronized(lock) {
            if (handle != 0L) NativeLib.nativeCancelRequest(handle)
        }
    }

    internal fun requireHandle(): Long = synchronized(lock) {
        check(handle != 0L) { "CancelToken is closed" }
        handle
    }

    override fun close() {
        synchronized(lock) {
            if (handle != 0L) {
                NativeLib.nativeCancelDestroy(handle)
                handle = 0L
            }
        }
    }
}
